package com.chrono.chrono.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class BackupService {

    @Scheduled(cron = "0 45 23 * * *")
    public void backupDatabase() {
        String dbUser     = System.getenv().getOrDefault("MYSQL_USER", "root");
        String dbPassword = System.getenv().getOrDefault("MYSQL_PASSWORD", "");
        String dbName     = System.getenv().getOrDefault("MYSQL_DATABASE", "chrono_db");

        /*  neue, plattform-unabhängige Bin- und Zielpfade  */
        String dumpBin = System.getenv().getOrDefault(
                "MYSQLDUMP_BIN", "/usr/bin/mysqldump");
        String targetDir = System.getenv().getOrDefault(
                "BACKUP_DIR", "/var/backup");

        /* falls mysqldump nicht vorhanden → Log & Exit   */
        if (!new File(dumpBin).exists()) {
            System.err.println("mysqldump not found at " + dumpBin + " – skip backup.");
            return;
        }

        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String out  = targetDir + "/backup_" + date + ".sql";

        List<String> cmd = List.of(
                dumpBin, "--user="+dbUser, "--password="+dbPassword,
                "--result-file="+out, dbName);

        try {
            int ec = new ProcessBuilder(cmd).inheritIO().start().waitFor();
            System.out.println(ec==0 ? "Backup ok: "+out : "Backup ERR, exit "+ec);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
