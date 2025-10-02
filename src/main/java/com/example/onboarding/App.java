package com.example.onboarding;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    private static final Set<String> ALLOWED_TYPES = Set.of("WIRE","ACH","DEPOSIT");
    private static final Set<String> ALLOWED_CCY = Set.of("CAD","USD");
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar app.jar <path/to/raw.csv>");
            System.exit(1);
        }
        DATE_FMT.setLenient(false);

        String csvPath = args[0];
        Map<String,String> mapping = loadMapping();
        String dbUrl = getenv("DB_URL", "jdbc:postgresql://localhost:5432/verafin_demo");
        String dbUser = getenv("DB_USER", System.getProperty("user.name"));
        String dbPass = getenv("DB_PASS", "");

        List<Map<String,String>> clean = new ArrayList<>();
        List<Map<String,String>> rejected = new ArrayList<>();

        List<Map<String,String>> rows = readCsv(csvPath);
        int rowNum = 1;
        for (Map<String,String> raw : rows) {
            Map<String,String> std = applyMapping(raw, mapping);
            List<String> errors = validate(std);
            std.put("source_file", Paths.get(csvPath).getFileName().toString());
            std.put("raw_row_number", Integer.toString(rowNum));
            if (errors.isEmpty()) {
                clean.add(std);
            } else {
                std.put("errors", String.join("; ", errors));
                rejected.add(std);
            }
            rowNum++;
        }

        Path outDir = Paths.get("data","output");
        Files.createDirectories(outDir);
        writeCsv(outDir.resolve("clean.csv").toString(), clean, List.of(
                "account_number","counterparty_account","transaction_type",
                "amount","currency_code","transaction_timestamp","source_file","raw_row_number"
        ));
        writeCsv(outDir.resolve("rejected.csv").toString(), rejected, List.of(
                "account_number","counterparty_account","transaction_type",
                "amount","currency_code","transaction_timestamp","source_file","raw_row_number","errors"
        ));

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            conn.setAutoCommit(false);
            String sql = "INSERT INTO verafin_standard.transactions " +
                    "(account_number, counterparty_account, transaction_type, amount, currency_code, transaction_timestamp, source_file, raw_row_number) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map<String,String> r : clean) {
                    ps.setString(1, r.get("account_number"));
                    ps.setString(2, r.get("counterparty_account"));
                    ps.setString(3, r.get("transaction_type"));
                    ps.setBigDecimal(4, new java.math.BigDecimal(r.get("amount")));
                    ps.setString(5, r.get("currency_code"));
                    ps.setDate(6, java.sql.Date.valueOf(r.get("transaction_timestamp")));
                    ps.setString(7, r.get("source_file"));
                    ps.setInt(8, Integer.parseInt(r.get("raw_row_number")));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        }

        log.info("Clean rows inserted: {}", clean.size());
        log.info("Rejected rows: {}", rejected.size());
    }

    static Map<String,String> loadMapping() throws IOException {
        try (InputStream in = App.class.getClassLoader().getResourceAsStream("field_mappings.json")) {
            if (in == null) throw new FileNotFoundException("field_mappings.json not found");
            String json = new String(in.readAllBytes());
            Map<String,String> map = new HashMap<>();
            json = json.trim().replaceAll("[\\{\\}\"]","");
            for (String part : json.split(",")) {
                String[] kv = part.split(":");
                map.put(kv[0].trim(), kv[1].trim());
            }
            return map;
        }
    }

    static String getenv(String k, String def) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? def : v;
    }

    static List<Map<String,String>> readCsv(String path) throws IOException, CsvValidationException {
        List<Map<String,String>> out = new ArrayList<>();
        try (CSVReader r = new CSVReader(new FileReader(path))) {
            String[] header = r.readNext();
            String[] row;
            while ((row = r.readNext()) != null) {
                Map<String,String> m = new LinkedHashMap<>();
                for (int i = 0; i < header.length; i++) {
                    m.put(header[i], i < row.length ? row[i] : "");
                }
                out.add(m);
            }
        }
        return out;
    }

    static Map<String,String> applyMapping(Map<String,String> raw, Map<String,String> mapping) {
        Map<String,String> std = new LinkedHashMap<>();
        for (Map.Entry<String,String> e : mapping.entrySet()) {
            std.put(e.getValue(), raw.getOrDefault(e.getKey(), "").trim());
        }
        return std;
    }

    static List<String> validate(Map<String,String> r) {
        List<String> errs = new ArrayList<>();

        if (!r.getOrDefault("account_number","").matches("^\\d{8,16}$"))
            errs.add("account_number must be 8-16 digits");
        String cp = r.getOrDefault("counterparty_account","");
        if (!cp.isBlank() && !cp.matches("^\\d{8,16}$"))
            errs.add("counterparty_account must be 8-16 digits or blank");

        String type = r.getOrDefault("transaction_type","");
        if (!ALLOWED_TYPES.contains(type))
            errs.add("transaction_type must be one of " + ALLOWED_TYPES);

        try {
            java.math.BigDecimal amt = new java.math.BigDecimal(r.getOrDefault("amount",""));
            if (amt.compareTo(java.math.BigDecimal.ZERO) < 0) errs.add("amount cannot be negative");
        } catch (Exception ex) {
            errs.add("amount must be numeric");
        }

        String ccy = r.getOrDefault("currency_code","");
        if (!ALLOWED_CCY.contains(ccy))
            errs.add("currency_code must be CAD or USD");

        String ts = r.getOrDefault("transaction_timestamp","");
        try {
            new java.sql.Date(new java.text.SimpleDateFormat("yyyy-MM-dd").parse(ts).getTime());
        } catch (Exception ex) {
            errs.add("transaction_timestamp must be yyyy-MM-dd");
        }

        return errs;
    }

    static void writeCsv(String path, List<Map<String,String>> rows, List<String> header) throws IOException {
        try (BufferedWriter w = java.nio.file.Files.newBufferedWriter(java.nio.file.Paths.get(path))) {
            w.write(String.join(",", header));
            w.newLine();
            for (Map<String,String> r : rows) {
                List<String> vals = new ArrayList<>();
                for (String h : header) {
                    String v = r.getOrDefault(h, "");
                    if (v.contains(",") || v.contains("\"")) {
                        v = "\"" + v.replace("\"","\"\"") + "\"";
                    }
                    vals.add(v);
                }
                w.write(String.join(",", vals));
                w.newLine();
            }
        }
    }
}
