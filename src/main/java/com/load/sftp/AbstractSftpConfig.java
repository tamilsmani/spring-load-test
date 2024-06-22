package com.load.sftp;

import com.jcraft.jsch.ChannelSftp;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.PollerSpec;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizer;
import org.springframework.integration.sftp.inbound.SftpInboundFileSynchronizingMessageSource;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.File;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
public abstract class AbstractSftpConfig implements InitializingBean {

    @Getter
    @Value("${${sftp.serviceName}.sftp.inbound.dir}")
    protected String sftpInboundDirectory;

    @Getter
    @Value("${${sftp.serviceName}.sftp.local.dir}")
    protected String sftpLocalDownloadedDirectory;

    @Getter
    @Value("${${sftp.serviceName}.sftp.error.dir}")
    protected String sftpErrorDirectory;

    @Getter
    @Value("${${sftp.serviceName}.sftp.listern.concurrency:1}")
    protected int sftpListernConcurrency;

    @Autowired
    IntegrationFlowContext integrationFlowContext;

    @Autowired
    ApplicationContext applicationContext;

    @Value("${${sftp.serviceName}.sftp.host}")
    String sftpHost;

    @Value("${${sftp.serviceName}.sftp.port}")
    Integer sftpPort;

    @Value("${${sftp.serviceName}.sftp.user}")
    String sftpUser;

    @Value("${${sftp.serviceName}.sftp.pwd}")
    String sftpPasword;

    @Value("${${sftp.serviceName}.sftp.file.filter.pattern}")
    String sftpInboundFileFilterPattern;

    @Getter
    @Value("${${sftp.serviceName}.sftp.outbound.dir}")
    String sftpOutboundDirectory;

    @Getter
    @Value("${${sftp.serviceName}.sftp.archive.dir}")
    String sftpArchiveDirectory;

    @Value("${${sftp.serviceName}.sftp.max.files.per.poll:1}")
    int maxFetchSize;

    @Value("${${sftp.serviceName}.sftp.poller.cron.expression}")
    String cronExpression;

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @Getter
    SftpRemoteFileTemplate sftpTemplate;

    SftpInboundFileSynchronizingMessageSource sftpInboundFileSyncMessageSource;
    IntegrationFlow flow;

    public SessionFactory sftpSessionFactory() {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost(sftpHost);
        factory.setPort(sftpPort);
        factory.setUser(sftpUser);
        factory.setPassword(sftpPasword);
        factory.setAllowUnknownKeys(true);
        sftpTemplate = new SftpRemoteFileTemplate(factory);
        return new CachingSessionFactory<>(factory);
    }

    public TaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Is the minimum number of threads that remain active at any given point of time
        executor.setCorePoolSize(sftpListernConcurrency);
        // The maxPoolSize relies on queueCapacity because ThreadPoolTaskExecutor
        // creates a new thread only if the number of items in the queue exceeds queue capacity.
        executor.setMaxPoolSize(sftpListernConcurrency);
        executor.setQueueCapacity(sftpListernConcurrency);
        executor.setThreadNamePrefix("sftpth-");
        // Incase of 'Q' is full, we have to delegate the task to the caller thread to run the task.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }

   public SftpInboundFileSynchronizer sftpInboundFileSynchronizer(SessionFactory sftpSessionFactory ) {

        SftpInboundFileSynchronizer fileSynchronizer = new SftpInboundFileSynchronizer(sftpSessionFactory);
        fileSynchronizer.setRemoteDirectory(sftpInboundDirectory);
        return fileSynchronizer;
    }

    public SftpInboundFileSynchronizingMessageSource sftpMessageSource(SftpInboundFileSynchronizer sftpInboundFileSynchronizer) {
        SftpInboundFileSynchronizingMessageSource source = new SftpInboundFileSynchronizingMessageSource(
                sftpInboundFileSynchronizer);
        source.setLocalDirectory(new File(sftpLocalDownloadedDirectory));
        source.setAutoCreateLocalDirectory(true);
        source.setUseWatchService(true);
        source.setMaxFetchSize(maxFetchSize);
        source.setLocalFilter(new SimplePatternFileListFilter(sftpInboundFileFilterPattern));
        return source;
    }

    @Override
    public void afterPropertiesSet() {
        SessionFactory sessionFactory = sftpSessionFactory();

        SftpInboundFileSynchronizer sftpInboundFileSynchronizer = sftpInboundFileSynchronizer(sessionFactory);
        sftpInboundFileSyncMessageSource = sftpMessageSource(sftpInboundFileSynchronizer);

        PollerSpec pollerSpec = Pollers.cron(cronExpression)
                .maxMessagesPerPoll(maxFetchSize);

        flow = IntegrationFlow.
                from(sftpInboundFileSyncMessageSource, e -> e.poller(pollerSpec))
                .channel(MessageChannels.executor(threadPoolTaskExecutor()))
                .channel(toInputChannel())
                //.handle(applicationContext.getBean(serviceName + "ProcessorService"), "processMessage")
                .get();

        integrationFlowContext.registration(flow).id("Flow").register();
    }

    public MessageChannel toInputChannel() {
        DirectChannel channel = new DirectChannel();
        channel.subscribe(msg -> {

            log.info("Received file {} ", msg);
        });
        return channel;
    }

    public void moveFile(String sourceFilePath, String destFilePath) {
        if (sftpTemplate.exists(sourceFilePath))
            sftpTemplate.rename(sourceFilePath, destFilePath);
    }

    public void deleteRemoteFile(String filePath) {
        if (sftpTemplate.exists(filePath))
            sftpTemplate.remove(filePath);
    }

    public synchronized void startSftpPolling() {
        log.info("[SFTP]- Starting SFTP polling .... ");
        sftpInboundFileSyncMessageSource.start();
    }

    public synchronized void stopSftpPolling() {
        log.info("[SFTP]- Stopping SFTP polling ");
        if (isSftpPollingInprogress())
            sftpInboundFileSyncMessageSource.stop();
        log.info("[SFTP]-[{}] - Stopped  SFTP polling");
    }

    public boolean isSftpPollingInprogress() {
        return sftpInboundFileSyncMessageSource.isRunning();
    }

}
