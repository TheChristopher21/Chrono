package com.chrono.chrono.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class BackupService {

    // Täglich um 23:30 Uhr wird das Backup ausgeführt
    @Scheduled(cron = "0 45 23 * * *")
    public void backupDatabase() {
        // Passe diese Variablen an deine Umgebung an:
        String dbUser = "root";           // z.B. "root"
        String dbPassword = "715841";       // z.B. "secret"
        String dbName = "chrono_db";       // z.B. "chrono_db"

        // Absolute Pfade laut deiner Angabe:
        String desktopPath = "C:\\Users\\siefe\\Desktop";
        String mysqlpumpPath = "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysqlpump.exe";

        // Dateiname mit aktuellem Datum, z.B. backup_2025-03-30.sql
        String dateString = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String backupFile = desktopPath + "\\backup_" + dateString + ".sql";

        // Erstelle den mysqlpump-Befehl als Liste von Strings
        // Hinweis: mysqlpump verwendet "--user=" und "--password=" sowie "--result-file="
        List<String> commands = new ArrayList<>();
        commands.add(mysqlpumpPath);
        commands.add("--user=" + dbUser);
        commands.add("--password=" + dbPassword);
        commands.add(dbName);
        commands.add("--result-file=" + backupFile);

        try {
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Lese Standardausgabe und Fehlerausgabe (für Debugging)
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Datenbank-Backup erfolgreich erstellt: " + backupFile);
                File file = new File(backupFile);
                if (file.exists()) {
                    System.out.println("Backup-Datei gefunden: " + file.getAbsolutePath());
                } else {
                    System.err.println("Backup-Datei wurde nicht gefunden.");
                }
            } else {
                System.err.println("Fehler beim Datenbank-Backup (Exit Code " + exitCode + "):\n" + output);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
