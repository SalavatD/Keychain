package ru.salavatdautov;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;

public class AesCrypt {

    public enum Mode {
        DEFAULT,
        ENCRYPT,
        DECRYPT
    }

    private static final int BUFFER_SIZE = 64 * 1024;

    private static final int MAX_PASSWORD_LENGTH = 1024;

    private static final int AES_BLOCK_SIZE = 16;

    private static byte[] stretch(String password, byte[] iv1) throws NoSuchAlgorithmException {

        byte[] result = Arrays.copyOf(iv1, iv1.length + 16);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        for (int i = 0; i < 8192; i++) {
            digest.update(result);
            digest.update(password.getBytes(StandardCharsets.UTF_16LE));
            result = digest.digest();
        }

        return result;
    }

    private static byte[] getSecureRandomBytes(int count) {
        byte[] result = new byte[count];
        new SecureRandom().nextBytes(result);
        return result;
    }

    public void openFiles(String inputFilePath, String outputFilePath, String password, Mode mode) throws GeneralSecurityException, IOException {

        File inputFile = new File(inputFilePath);
        File outputFile = new File(outputFilePath);

        if (outputFile.exists()) {
            throw new IllegalArgumentException("Output file exists.");
        }

        FileInputStream inputStream = new FileInputStream(inputFile);
        FileOutputStream outputStream = new FileOutputStream(outputFile);

        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password is too long.");
        }

        if (mode == Mode.ENCRYPT) {
            encryptStream(inputStream, outputStream, password);
        } else {
            decryptStream(inputStream, outputStream, password, inputFile.length());
        }

        outputStream.close();
        inputStream.close();
    }

    public void encryptFile(String inputFilePath, String outputFilePath, String password) throws GeneralSecurityException, IOException {
        openFiles(inputFilePath, outputFilePath, password, Mode.ENCRYPT);
    }

    public void encryptStream(InputStream inputStream, OutputStream outputStream, String password) throws GeneralSecurityException, IOException {

        IvParameterSpec iv0 = new IvParameterSpec(getSecureRandomBytes(AES_BLOCK_SIZE));

        SecretKeySpec key0 = new SecretKeySpec(getSecureRandomBytes(32), "AES");

        Cipher cipher0 = Cipher.getInstance("AES/CBC/NoPadding");
        cipher0.init(Cipher.ENCRYPT_MODE, key0, iv0);

        Mac hmac0 = Mac.getInstance("HmacSHA256");
        hmac0.init(new SecretKeySpec(key0.getEncoded(), "HmacSHA256"));

        IvParameterSpec iv1 = new IvParameterSpec(getSecureRandomBytes(AES_BLOCK_SIZE));

        SecretKeySpec key1 = new SecretKeySpec(stretch(password, iv1.getIV()), "AES");

        Cipher cipher1 = Cipher.getInstance("AES/CBC/NoPadding");
        cipher1.init(Cipher.ENCRYPT_MODE, key1, iv1);

        Mac hmac1 = Mac.getInstance("HmacSHA256");
        hmac1.init(new SecretKeySpec(key1.getEncoded(), "HmacSHA256"));

        byte[] cIVkey = new byte[AES_BLOCK_SIZE + 32];
        cipher1.update(iv0.getIV(), 0, 16, cIVkey);
        cipher1.doFinal(key0.getEncoded(), 0, 32, cIVkey, 16);

        outputStream.write(iv1.getIV());
        outputStream.write(cIVkey);
        outputStream.write(hmac1.doFinal(cIVkey));

        byte fs16 = 0;
        int available;
        byte[] fdata = new byte[BUFFER_SIZE];

        while ((available = inputStream.available()) > 0) {
            int bufferSize = available < BUFFER_SIZE ? AES_BLOCK_SIZE : BUFFER_SIZE;

            int bytesRead = inputStream.read(fdata, 0, bufferSize);

            cipher0.update(fdata, 0, bufferSize, fdata);
            hmac0.update(fdata, 0, bufferSize);

            outputStream.write(fdata, 0, bufferSize);

            fs16 = (byte) bytesRead;
        }

        outputStream.write(fs16);
        outputStream.write(hmac0.doFinal());
    }

    public void decryptFile(String inputFilePath, String outputFilePath, String password) throws GeneralSecurityException, IOException {
        openFiles(inputFilePath, outputFilePath, password, Mode.DECRYPT);
    }

    public void decryptStream(InputStream inputStream, OutputStream outputStream, String password, long inputFileSize) throws GeneralSecurityException, IOException {

        long position = 0;
        int bytesRead;

        byte[] buffer = new byte[AES_BLOCK_SIZE];
        bytesRead = inputStream.read(buffer);
        if (bytesRead < AES_BLOCK_SIZE) {
            throw new IllegalArgumentException("File is corrupted.");
        }
        position += bytesRead;

        IvParameterSpec iv1 = new IvParameterSpec(buffer);

        SecretKeySpec key1 = new SecretKeySpec(stretch(password, iv1.getIV()), "AES");

        byte[] cIVkey = new byte[AES_BLOCK_SIZE + 32];
        bytesRead = inputStream.read(cIVkey);
        if (bytesRead < AES_BLOCK_SIZE + 32) {
            throw new IllegalArgumentException("File is corrupted.");
        }
        position += bytesRead;

        byte[] hmac1 = new byte[32];
        bytesRead = inputStream.read(hmac1);
        if (bytesRead < 32) {
            throw new IllegalArgumentException("File is corrupted.");
        }
        position += bytesRead;

        Mac hmac1Act = Mac.getInstance("HmacSHA256");
        hmac1Act.init(new SecretKeySpec(key1.getEncoded(), "HmacSHA256"));

        if (!Arrays.equals(hmac1, hmac1Act.doFinal(cIVkey))) {
            throw new InvalidAlgorithmParameterException("Wrong password (or file is corrupted).");
        }

        Cipher cipher1 = Cipher.getInstance("AES/CBC/NoPadding");
        cipher1.init(Cipher.DECRYPT_MODE, key1, iv1);

        buffer = cipher1.doFinal(cIVkey);

        IvParameterSpec iv0 = new IvParameterSpec(Arrays.copyOfRange(buffer, 0, AES_BLOCK_SIZE));

        SecretKeySpec key0 = new SecretKeySpec(Arrays.copyOfRange(buffer, AES_BLOCK_SIZE, buffer.length), "AES");

        Cipher cipher0 = Cipher.getInstance("AES/CBC/NoPadding");
        cipher0.init(Cipher.DECRYPT_MODE, key0, iv0);

        Mac hmac0Act = Mac.getInstance("HmacSHA256");
        hmac0Act.init(new SecretKeySpec(key0.getEncoded(), "HmacSHA256"));

        buffer = new byte[BUFFER_SIZE];
        while (position < inputFileSize - 1 - 32 - AES_BLOCK_SIZE) {
            int bufferSize = Math.min(BUFFER_SIZE, (int) (inputFileSize - position - 1 - 32 - AES_BLOCK_SIZE));

            bytesRead = inputStream.read(buffer, 0, bufferSize);

            hmac0Act.update(buffer, 0, bufferSize);

            outputStream.write(cipher0.update(buffer, 0, bufferSize), 0, bufferSize);

            position += bytesRead;

        }

        buffer = new byte[AES_BLOCK_SIZE];
        if (position != inputFileSize - 1 - 32) {
            bytesRead = inputStream.read(buffer);
            if (bytesRead < AES_BLOCK_SIZE) {
                throw new IllegalArgumentException("File is corrupted.");
            }
        } else {
            buffer = new byte[0];
        }

        hmac0Act.update(buffer);

        byte[] fs16 = new byte[1];
        bytesRead = inputStream.read(fs16);
        if (bytesRead != 1) {
            throw new IllegalArgumentException("File is corrupted.");
        }

        buffer = cipher0.doFinal(buffer);

        int toRemove = (AES_BLOCK_SIZE - fs16[0]) % AES_BLOCK_SIZE;

        outputStream.write(buffer, 0, buffer.length - toRemove);

        byte[] hmac0 = new byte[32];
        bytesRead = inputStream.read(hmac0);
        if (bytesRead != 32) {
            throw new IllegalArgumentException("File is corrupted.");
        }

        if (!Arrays.equals(hmac0, hmac0Act.doFinal())) {
            throw new InvalidAlgorithmParameterException("Bad HMAC (file is corrupted).");
        }
    }
}
