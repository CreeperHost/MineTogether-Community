package net.creeperhost.minetogethercommunity.orderform;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class WorldUploader {

    private static final Logger LOGGER = LogManager.getLogger("MineTogether World Upload");
    private static final ExecutorService UPLOAD_EXECUTOR = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setDaemon(true).setNameFormat("MT World Upload").build());
    private static final String USER_AGENT = "MineTogether/1.0.0 Minecraft/1.8.9";

    private final Path worldFolder;
    private volatile int stage;
    private volatile double uploadProgress;
    private volatile String error;
    private volatile String resultFileURL;
    private volatile boolean finished;
    private Future<?> uploadTask;
    private Path tempZipFile;

    public WorldUploader(Path worldFolder) {
        this.worldFolder = worldFolder;
    }

    public boolean running() {
        return uploadTask != null && !uploadTask.isDone();
    }

    public String getStatus() {
        if (stage == 0) return "minetogether.gui.order.upload_stage.start";
        if (stage == 1) return "minetogether.gui.order.upload_stage.compress";
        if (stage == 2) return "minetogether.gui.order.upload_stage.upload";
        return "";
    }

    public double getUploadProgress() {
        return uploadProgress;
    }

    public boolean errored() {
        return error != null;
    }

    public String getError() {
        return error;
    }

    public boolean isFinished() {
        return finished;
    }

    public String getResultFileURL() {
        return resultFileURL;
    }

    public void start() {
        if (running()) return;
        stage = 0;
        finished = false;
        error = null;
        resultFileURL = null;
        uploadProgress = 0;
        uploadTask = UPLOAD_EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                doUpload();
            }
        });
    }

    public void cancel() {
        if (uploadTask != null) {
            uploadTask.cancel(true);
            uploadTask = null;
        }
    }

    private void doUpload() {
        try {
            stage = 1;
            compress();
            stage = 2;
            upload();
            finished = true;
        } catch (Throwable ex) {
            LOGGER.error("An error occurred while uploading world {}", worldFolder, ex);
            error = ex.getMessage() == null ? "World upload failed" : ex.getMessage();
        } finally {
            uploadTask = null;
            if (tempZipFile != null) {
                try {
                    Files.deleteIfExists(tempZipFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void compress() throws IOException {
        tempZipFile = Files.createTempFile("minetogether-world-upload", ".zip");
        final Path folderName = Paths.get(worldFolder.getFileName().toString());
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tempZipFile)))) {
            Files.walkFileTree(worldFolder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                    if (path.endsWith("session.lock")) return FileVisitResult.CONTINUE;
                    String entryName = folderName.resolve(worldFolder.relativize(path)).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entryName));
                    try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
                        copy(input, zip, -1L);
                    }
                    zip.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void upload() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://transfer.ch.tools/world.zip").openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setDoOutput(true);
        long length = Files.size(tempZipFile);
        connection.setFixedLengthStreamingMode(length);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(120000);

        try (InputStream input = new BufferedInputStream(Files.newInputStream(tempZipFile));
             OutputStream output = new BufferedOutputStream(connection.getOutputStream())) {
            copy(input, output, length);
        }

        int code = connection.getResponseCode();
        InputStream response = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = readString(response);
        connection.disconnect();
        if (code != 200) {
            throw new IOException("Upload failed with status code " + code + ": " + body);
        }
        resultFileURL = body.trim();
    }

    private void copy(InputStream input, OutputStream output, long progressLength) throws IOException {
        byte[] buffer = new byte[8192];
        long read = 0;
        int len;
        while ((len = input.read(buffer)) != -1) {
            output.write(buffer, 0, len);
            if (progressLength > 0) {
                read += len;
                uploadProgress = Math.min(1D, read / (double) progressLength);
            }
        }
    }

    private String readString(InputStream input) throws IOException {
        if (input == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
