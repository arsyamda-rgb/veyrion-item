package dev.veyrion.item.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Generates one zip per requested version from a single content definition. */
public final class PackGenerator {
    private PackGenerator() {}

    public record Result(McVersion version, Path file, String sha1) {}

    public static List<Result> generate(PackContent content, List<McVersion> versions, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        Map<Integer, byte[]> perFormat = new HashMap<>(); // versions sharing a format share bytes
        List<Result> results = new ArrayList<>();
        StringBuilder manifest = new StringBuilder("version  pack_format  sha1                                      file\n");

        for (McVersion v : versions) {
            byte[] zip = perFormat.get(v.packFormat());
            if (zip == null) {
                zip = zip(PackBuilder.build(content, v));
                perFormat.put(v.packFormat(), zip);
            }
            Path file = outDir.resolve("pack-" + v.name() + ".zip");
            Files.write(file, zip);
            String sha1 = sha1(zip);
            results.add(new Result(v, file, sha1));
            manifest.append(String.format("%-8s %-12d %s  %s%n", v.name(), v.packFormat(), sha1, file.getFileName()));
        }
        Files.writeString(outDir.resolve("manifest.txt"), manifest.toString(), StandardCharsets.UTF_8);
        return results;
    }

    /** Deterministic zip: sorted entries, fixed timestamps, so the SHA-1 only changes when content changes. */
    static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> e : new TreeMap<>(files).entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                ze.setTime(946684800000L);
                zos.putNextEntry(ze);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    static String sha1(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
