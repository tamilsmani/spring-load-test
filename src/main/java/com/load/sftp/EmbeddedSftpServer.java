package com.load.sftp;

import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.ServerBuilder;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Collections;

@Slf4j
public class EmbeddedSftpServer implements InitializingBean, SmartLifecycle {

    public final int PORT = 10023;

    private final SshServer server = new ServerBuilder().build();

    private volatile boolean running;

    @Value("${${sftp.serviceName}.sftp.port}")
    private int port;

    @Value("${${sftp.serviceName}.sftp.user}")
    private String sftpUserame;

    @Value("${${sftp.serviceName}.sftp.pwd}")
    String sftpPassword;

    @Value("${${sftp.serviceName}.sftp.inbound.dir}")
    String sftpInboundDirectory;

    @Value("${${sftp.serviceName}.sftp.outbound.dir:}")
    String sftpOutboundDirectory;

    @Value("${${sftp.serviceName}.sftp.error.dir:}")
    String sftpErrorDirectory;

    @Value("${${sftp.serviceName}.sftp.archive.dir:}")
    String sftpArchiveDirectory;

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //cleanup();
        Files.createDirectories(Paths.get(System.getProperty("user.dir"), "sftp", sftpInboundDirectory.substring(1)));
        Files.createDirectories(Paths.get(System.getProperty("user.dir"), "sftp", sftpOutboundDirectory.substring(1)));
        Files.createDirectories(Paths.get(System.getProperty("user.dir"), "sftp", sftpErrorDirectory.substring(1)));
        Files.createDirectories(Paths.get(System.getProperty("user.dir"), "sftp", sftpArchiveDirectory.substring(1)));

        // final PublicKey allowedKey = decodePublicKey();
        server.setPasswordAuthenticator(new PasswordAuthenticator() {
            @Override
            public boolean authenticate(String username, String password, ServerSession session) throws PasswordChangeRequiredException {
                return sftpUserame.equals(username) && sftpPassword.equals(password);
            }
        });
//        this.server.setPublickeyAuthenticator(new PublickeyAuthenticator() {
//
//            @Override
//            public boolean authenticate(String username, PublicKey key, ServerSession session) {
//                return key.equals(allowedKey);
//            }
//
//        });
        this.server.setPort(this.port);
        this.server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Files.createTempFile("host_file", ".ser")));
        this.server.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        server.setFileSystemFactory(new VirtualFileSystemFactory(Files.createTempDirectory("SFTP_TEMP")));
        this.setHomeFolder(getSFTPPath());

        start();
        log.info("Embedded sftp server started");
    }

    public void setHomeFolder(Path path) {
        server.setFileSystemFactory(new VirtualFileSystemFactory(path));
    }


    private PublicKey decodePublicKey() throws Exception {
        InputStream stream = new ClassPathResource("/keys/sftp_rsa.pub").getInputStream();
        byte[] decodeBuffer = Base64.getDecoder().decode(StreamUtils.copyToByteArray(stream));
        ByteBuffer bb = ByteBuffer.wrap(decodeBuffer);
        int len = bb.getInt();
        byte[] type = new byte[len];
        bb.get(type);
        if ("ssh-rsa".equals(new String(type))) {
            BigInteger e = decodeBigInt(bb);
            BigInteger m = decodeBigInt(bb);
            RSAPublicKeySpec spec = new RSAPublicKeySpec(m, e);
            return KeyFactory.getInstance("RSA").generatePublic(spec);

        } else {
            throw new IllegalArgumentException("Only supports RSA");
        }
    }

    private BigInteger decodeBigInt(ByteBuffer bb) {
        int len = bb.getInt();
        byte[] bytes = new byte[len];
        bb.get(bytes);
        return new BigInteger(bytes);
    }

    @Override
    public boolean isAutoStartup() {
        return PORT == this.port;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void start() {
        try {
            server.start();
            this.running = true;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    @SneakyThrows
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public void stop() {
        if (this.running) {
            try {
                server.stop(true);
                server.close(true);
                Thread.sleep(5000);
                cleanup();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                this.running = false;
            }
        }

    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    public SshServer getServer() {
        return server;
    }

    public String getSftpUserame() {
        return sftpUserame;
    }

    public void setSftpUserame(String sftpUserame) {
        this.sftpUserame = sftpUserame;
    }

    public String getSftpPassword() {
        return sftpPassword;
    }

    public void setSftpPassword(String sftpPassword) {
        this.sftpPassword = sftpPassword;
    }

    @PreDestroy
    public void tearDown() throws Exception {
        if (isRunning()) {
            stop();
        }
        cleanup();
    }

    public void cleanup() throws Exception {
        Path dir = getSFTPPath();
        if (dir.toFile().exists())
            FileUtils.deleteDirectory(dir.toFile());
    }

    public Path getSFTPPath() {
        return (Path) Paths.get(System.getProperty("user.dir"), "sftp");
    }

    public String getSftpInboundDirectory() {
        return sftpInboundDirectory;
    }

    public String getSftpOutboundDirectory() {
        return sftpOutboundDirectory;
    }

    public void copyFileToSFTPInboundDirectory(String inputFile) throws Exception {
        File inboundDir = new File(getSFTPPath().toFile().getAbsoluteFile() + "/" + sftpInboundDirectory);
        inboundDir.mkdir();

        InputStream sourceInputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(inputFile);

        String destinationFileName = inputFile.lastIndexOf('/') == -1 ? inputFile : inputFile.substring(inputFile.lastIndexOf('/') + 1);

        File destinationFile = new File(inboundDir.getAbsolutePath() + '/' + destinationFileName);
        FileUtils.copyInputStreamToFile(sourceInputStream, destinationFile);
        log.info("Published to SFTP Inbound folder - {}", destinationFile);
    }
}