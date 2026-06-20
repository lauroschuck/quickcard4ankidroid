package com.github.lauroschuck.quickcard4ankidroid.processor;

import com.github.lauroschuck.quickcard4ankidroid.processor.pos.PosTranslationData;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PosTranslationSetter {

    private static final Pattern DATABASE_FILE_NAME_PATTERN = Pattern.compile("wiktionary_kaikki_[a-z]{2}-([a-z]{2})_v\\d+_\\d+.db");

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println(
                    "Usage: java PosTranslationSetter <native_language> <target_out_folder> <pos> <translation>");
            System.exit(1);
        }

        String nativeLanguage = args[0];
        File outFolder = new File(args[1]);
        if (!outFolder.exists() || !outFolder.isDirectory()) {
            System.out.printf("Out folder %s does nto exist or is not a folder", outFolder);
            System.exit(2);
        }
        File posTranslationsCumulativeFile = new File(outFolder, "pos_translations_cumulative.json");
        String pos = args[2].trim();
        String translation = args[3].trim();
        if (translation.isEmpty()) {
            System.out.println("Translation must not be blank");
            System.exit(3);
        }

        Map<File, Connection> databaseConnections = null;
        try {
            PosTranslationData posTranslationData = PosTranslationData.read(posTranslationsCumulativeFile);

            databaseConnections = getDatabaseConnections(outFolder, nativeLanguage);
            if (databaseConnections.isEmpty()) {
                System.out.printf("No '%s' databases found under %s%n", nativeLanguage, outFolder);
                System.exit(1);
            }

            // First, make sure all values are null
            for (Map.Entry<File, Connection> entry : databaseConnections.entrySet()) {
                assertUndefined(entry.getValue(), entry.getKey(), pos);
            }
            // The same on the JSON file
            String posFileValue = posTranslationData.getTranslation(nativeLanguage, pos);
            if (posFileValue == null) {
                throw new IllegalStateException("No such entry in the cumulative file");
            } else if (!posFileValue.isEmpty()) {
                throw new IllegalStateException(String.format("Cumulative file has '%s' for '%s'", posFileValue, pos));
            }

            // Now, set each value to the desired translation
            for (Map.Entry<File, Connection> entry : databaseConnections.entrySet()) {
                setTranslation(entry.getValue(), pos, translation);
            }
            // And the JSON file too
            posTranslationData.setTranslation(nativeLanguage, pos, translation);
            posTranslationData.write(posTranslationsCumulativeFile, true);
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println(4);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(5);
        } finally {
            boolean allSuccessful = true;
            if (databaseConnections != null) {
                for (Map.Entry<File, Connection> entry : databaseConnections.entrySet()) {
                    allSuccessful &= closeSilently(entry.getValue(), entry.getKey());
                }
            }
            if (!allSuccessful) {
                System.out.println("At least one connection did not close successfully");
                System.exit(6);
            }
        }
        System.out.printf(
                Locale.ROOT,
                """
                       [%s] Set '%s' translation as '%s'...
                            ... for %d databases under %s
                            ... on the POS translation file at %s
                       """,
                nativeLanguage, pos, translation, databaseConnections.size(), outFolder, posTranslationsCumulativeFile);
    }

    private static boolean closeSilently(Connection connection, File key) {
        try {
            connection.close();
        } catch (SQLException e) {
            System.out.println("Failed to successfully close connection " + key);
            return false;
        }
        return true;
    }

    public static Map<File, Connection> getDatabaseConnections(File folder, String nativeLanguage) {
        //DriverManager.getConnection("jdbc:sqlite:" + dbPath)
        File[] files = folder.listFiles((file, s) -> {
            Matcher matcher = DATABASE_FILE_NAME_PATTERN.matcher(s);
            return matcher.matches() && nativeLanguage.equals(matcher.group(1));
        });
        if (files == null) {
            return Map.of();
        }
        return Stream.of(files)
                .collect(Collectors.toMap(Function.identity(), f -> {
                    try {
                        return DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
                    } catch (SQLException e) {
                        throw new RuntimeException("Failed to create connection to file " + f, e);
                    }
                }));
    }

    public static void assertUndefined(Connection connection, File file, String pos) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT translation FROM pos_translations WHERE pos = ?")) {
            ps.setString(1, pos);
            if (!ps.execute()) {
                throw new RuntimeException("Unexpected result");
            }
            try (ResultSet rs = ps.getResultSet()) {
                if (!rs.next()) {
                    throw new RuntimeException(String.format("No value for '%s' on %s", pos, file));
                }
                String translation = rs.getString(1);
                if (!translation.isEmpty()) {
                    throw new RuntimeException(String.format("Unexpected translation '%s' for '%s' on %s", translation, pos, file));
                }
            }
        }
    }

    public static void setTranslation(Connection connection, String pos, String translation) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE pos_translations SET translation = ? WHERE pos = ?")) {
            ps.setString(1, translation);
            ps.setString(2, pos);
            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new RuntimeException("Unexpected result, updated=" + updated);
            }
        }
    }

}
