package ru.salavatdautov;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {

    private enum OS {
        WINDOWS,
        LINUX,
        MAC,
        UNKNOWN
    }

    private static final String DATA_FILE_NAME = "keychain_data.json";

    private static String keychainPassword = "";
    private static ArrayList<Record> records = new ArrayList<>();

    public static void main(String[] args) {
        try {
            parseArgs(args);
        } catch (IllegalArgumentException exception) {
            System.out.println("Argument error: " + exception.getMessage());
            System.out.println("Use -h for help.");
            exit(-1, false);
        }
        readFromFile();
        readPassword();
        mainLoop();
    }

    private static void exit(int code, boolean save) {
        if (save) {
            writeToFile();
        }
        System.exit(code);
    }

    private static void writeToFile() {
        sortRecords();
        try (FileWriter fileWriter = new FileWriter(DATA_FILE_NAME)) {
            new Gson().toJson(records, fileWriter);
        } catch (IOException exception) {
            System.out.println("Input/output error: " + exception.getMessage());
            exit(-1, false);
        }
    }

    private static void sortRecords() {
        Collections.sort(records);
    }

    private static void parseArgs(String[] args) {
        if (args.length == 1 && args[0].equals("-h")) {
            printHelp();
            exit(0, false);
        } else if (args.length == 1) {
            throw new IllegalArgumentException("Not enough arguments.");
        }
        if (args.length > 2) {
            throw new IllegalArgumentException("Too many arguments.");
        }
        if (args.length == 2 && args[0].equals("-p")) {
            keychainPassword = args[1];
        } else if (args.length == 2) {
            throw new IllegalArgumentException("Undefined arguments.");
        }
    }

    private static void printHelp() {
        System.out.println();
        System.out.println("About:");
        System.out.println("    Utility for storing credentials.");
        System.out.println("    Created 10.07.2021.");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("    keychain.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("    -p <password> Password");
        System.out.println("    -h            Call help");
        System.out.println();
    }

    private static void readFromFile() {
        File dataFile = new File(DATA_FILE_NAME);
        if (dataFile.exists() && dataFile.length() > 0) {
            try {
                records = new Gson().fromJson(new FileReader(dataFile), new TypeToken<ArrayList<Record>>() {
                }.getType());
            } catch (IOException exception) {
                System.out.println("Input/output error: " + exception.getMessage());
                exit(-1, false);
            } catch (JsonSyntaxException exception) {
                System.out.println("Json syntax error: " + exception.getMessage());
            }
        }
        sortRecords();
    }

    private static OS getOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return OS.WINDOWS;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return OS.LINUX;
        } else if (osName.contains("mac")) {
            return OS.MAC;
        }
        return OS.UNKNOWN;
    }

    private static void clearScreen() {
        switch (getOS()) {
            case WINDOWS:
                try {
                    clearScreenWindows();
                } catch (InterruptedException exception) {
                    System.out.println("Input/output error: " + exception.getMessage());
                    exit(-1, false);
                } catch (IOException exception) {
                    System.out.println("Interrupted error: " + exception.getMessage());
                    exit(-1, false);
                }
                break;
            case LINUX:
            case MAC:
                clearScreenUnix();
                break;
            case UNKNOWN:
            default:
                System.out.println("Unknown OS.");
                System.exit(0);
        }
    }

    private static void clearScreenUnix() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    private static void clearScreenWindows() throws IOException, InterruptedException {
        new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
    }

    private static void readPassword() {
        if (keychainPassword.isEmpty()) {
            Scanner scanner = new Scanner(System.in, "UTF-8");
            while (keychainPassword.isEmpty()) {
                System.out.print("Enter password: ");
                keychainPassword = scanner.nextLine();
            }
        }
    }

    private static void mainLoop() {
        while (true) {
            clearScreen();
            printMenu();
            selectAction(readNumber());
        }
    }

    private static void printMenu() {
        System.out.println("1. List of records");
        System.out.println("2. Details of record");
        System.out.println("3. Add new record");
        System.out.println("4. Edit record");
        System.out.println("5. Delete record");
        System.out.println("0. Exit");
        System.out.println();
        System.out.print("Action: ");
    }

    private static int readNumber() {
        return new Scanner(System.in).nextInt();
    }

    private static void selectAction(int optionNumber) {
        switch (optionNumber) {
            case 1:
                showAllRecords();
                break;
            case 2:
                showRecordDetails();
                break;
            case 3:
                newRecord();
                break;
            case 4:
                editRecord();
                break;
            case 5:
                deleteRecord();
                break;
            case 0:
            default:
                exit(0, true);
        }
    }

    private static void showAllRecords() {
        clearScreen();
        for (int i = 0; i < records.size(); i++) {
            System.out.printf("%d\n", i + 1);
            System.out.printf("Domain:     %s\n", records.get(i).domain);
            if (records.get(i).subdomains != null &&
                    records.get(i).subdomains.length > 0 &&
                    !records.get(i).subdomains[0].isEmpty()) {
                System.out.print("Subdomains: ");
                for (int j = 0; j < records.get(i).subdomains.length; j++) {
                    if (j == 0) {
                        System.out.println(records.get(i).subdomains[j]);
                    } else {
                        System.out.printf("            %s\n", records.get(i).subdomains[j]);
                    }
                }
            }
            System.out.printf("Date:       %s\n", dateToString(records.get(i).date));
            System.out.println();
        }
        pause();
    }

    private static String dateToString(Date date) {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);
        return String.format("%02d.%02d.%04d", calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR));
    }

    private static void pause() {
        System.out.println("Press Enter to continue...");
        try {
            System.in.read();
        } catch (IOException exception) {
            System.out.println("Input/output error: " + exception.getMessage());
            exit(-1, false);
        }
    }

    private static void showRecordDetails() {
        System.out.print("\nEnter record number: ");
        int position = readNumber() - 1;
        clearScreen();
        try {
            System.out.printf("Domain:     %s\n", records.get(position).domain);
            if (records.get(position).subdomains != null &&
                    records.get(position).subdomains.length > 0 &&
                    !records.get(position).subdomains[0].isEmpty()) {
                System.out.print("Subdomains: ");
                for (int i = 0; i < records.get(position).subdomains.length; i++) {
                    if (i == 0) {
                        System.out.println(records.get(position).subdomains[i]);
                    } else {
                        System.out.printf("            %s\n", records.get(position).subdomains[i]);
                    }
                }
            }
            System.out.printf("Date:       %s\n", dateToString(records.get(position).date));
            if (records.get(position).login != null && records.get(position).login.length > 0) {
                System.out.printf("Login:      %s\n", decryptString(records.get(position).login));
            }
            if (records.get(position).password != null && records.get(position).password.length > 0) {
                System.out.printf("Password:   %s\n", decryptString(records.get(position).password));
            }
            if (records.get(position).remark != null && records.get(position).remark.length > 0) {
                System.out.printf("Remark:     %s\n", decryptString(records.get(position).remark));
            }
        } catch (IndexOutOfBoundsException exception) {
            System.out.println("Wrong record number!");
        }
        System.out.println();
        pause();
    }

    private static String decryptString(byte[] data) {
        AesCrypt aesCrypt = new AesCrypt();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            aesCrypt.decryptStream(inputStream, outputStream, keychainPassword, data.length);
        } catch (GeneralSecurityException exception) {
            System.err.println("Internal error: " + exception.getMessage());
        } catch (IOException exception) {
            System.out.println("Input/output error: " + exception.getMessage());
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void newRecord() {
        clearScreen();
        Scanner scanner = new Scanner(System.in, "UTF-8");
        Record record = new Record();
        while (record.domain.isEmpty()) {
            System.out.print("Enter domain (required): ");
            record.domain = scanner.nextLine();
        }
        String subdomains = "";
        System.out.print("Enter subdomains (through a space): ");
        subdomains = scanner.nextLine();
        record.subdomains = subdomains.split(" ");
        String dateString = "";
        Date date = null;
        while (dateString.isEmpty() || date == null) {
            System.out.print("Enter date (required): ");
            dateString = scanner.nextLine();
            date = tryParseDate(dateString);
        }
        record.date = date;
        System.out.print("Enter login: ");
        String login = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        System.out.print("Enter remark: ");
        String remark = scanner.nextLine();
        if (!login.isEmpty()) {
            record.login = encryptString(login);
        }
        if (!password.isEmpty()) {
            record.password = encryptString(password);
        }
        if (!remark.isEmpty()) {
            record.remark = encryptString(remark);
        }
        records.add(record);
        sortRecords();
        System.out.println();
        pause();
    }

    private static Date tryParseDate(String dateString) {
        Date result;
        DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        dateFormat.setLenient(false);
        try {
            result = dateFormat.parse(dateString);
        } catch (ParseException e) {
            System.out.println("Date format: dd.MM.yyyy");
            return null;
        }
        return result;
    }

    private static byte[] encryptString(String data) {
        AesCrypt aesCrypt = new AesCrypt();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            aesCrypt.encryptStream(inputStream, outputStream, keychainPassword);
        } catch (GeneralSecurityException exception) {
            System.err.println("Internal error: " + exception.getMessage());
        } catch (IOException exception) {
            System.out.println("Input/output error: " + exception.getMessage());
        }
        return outputStream.toByteArray();
    }

    private static void editRecord() {
        System.out.print("\nEnter record number: ");
        int position = readNumber() - 1;
        clearScreen();
        try {
            showRecordDetailsForEdit(position);
            Scanner scanner = new Scanner(System.in, "UTF-8");
            System.out.print("\nEnter number of value to edit: ");
            switch (readNumber()) {
                case 1:
                    String domain = "";
                    while (domain.isEmpty()) {
                        System.out.print("Enter domain (required): ");
                        domain = scanner.nextLine();
                    }
                    records.get(position).domain = domain;
                    sortRecords();
                    break;
                case 2:
                    String subdomains = "";
                    System.out.print("Enter subdomains (through a space): ");
                    subdomains = scanner.nextLine();
                    records.get(position).subdomains = subdomains.split(" ");
                    break;
                case 3:
                    String dateString = "";
                    Date date = null;
                    while (dateString.isEmpty() || date == null) {
                        System.out.print("Enter date (required): ");
                        dateString = scanner.nextLine();
                        date = tryParseDate(dateString);
                    }
                    records.get(position).date = date;
                    sortRecords();
                    break;
                case 4:
                    System.out.print("Enter login: ");
                    String login = scanner.nextLine();
                    if (!login.isEmpty()) {
                        records.get(position).login = encryptString(login);
                    } else {
                        records.get(position).login = null;
                    }
                    break;
                case 5:
                    System.out.print("Enter password: ");
                    String password = scanner.nextLine();
                    if (!password.isEmpty()) {
                        records.get(position).password = encryptString(password);
                    } else {
                        records.get(position).password = null;
                    }
                    break;
                case 6:
                    System.out.print("Enter remark: ");
                    String remark = scanner.nextLine();
                    if (!remark.isEmpty()) {
                        records.get(position).remark = encryptString(remark);
                    } else {
                        records.get(position).remark = null;
                    }
                    break;
                default:
                    break;
            }
        } catch (IndexOutOfBoundsException exception) {
            System.out.println("Wrong record number!");
        }
        System.out.println();
        pause();
    }

    private static void showRecordDetailsForEdit(int position) {
        System.out.printf("1. Domain:     %s\n", records.get(position).domain);
        System.out.print("2. Subdomains: ");
        if (records.get(position).subdomains != null &&
                records.get(position).subdomains.length > 0 &&
                !records.get(position).subdomains[0].isEmpty()) {
            for (int i = 0; i < records.get(position).subdomains.length; i++) {
                if (i == 0) {
                    System.out.println(records.get(position).subdomains[i]);
                } else {
                    System.out.printf("               %s\n", records.get(position).subdomains[i]);
                }
            }
        } else {
            System.out.println("<null>");
        }
        System.out.printf("3. Date:       %s\n", dateToString(records.get(position).date));
        System.out.print("4. Login:      ");
        if (records.get(position).login != null && records.get(position).login.length > 0) {
            System.out.println(decryptString(records.get(position).login));
        } else {
            System.out.println("<null>");
        }
        System.out.print("5. Password:   ");
        if (records.get(position).password != null && records.get(position).password.length > 0) {
            System.out.println(decryptString(records.get(position).password));
        } else {
            System.out.println("<null>");
        }
        System.out.print("6. Remark:     ");
        if (records.get(position).remark != null && records.get(position).remark.length > 0) {
            System.out.println(decryptString(records.get(position).remark));
        } else {
            System.out.println("<null>");
        }
        System.out.println("0. Exit");
    }

    private static void deleteRecord() {
        System.out.print("\nEnter record number: ");
        int position = readNumber() - 1;
        clearScreen();
        try {
            records.remove(position);
            System.out.println("Record removed.");
        } catch (IndexOutOfBoundsException exception) {
            System.out.println("Wrong record number!");
        }
        System.out.println();
        pause();
    }
}
