package com.load;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.GenericTransformer;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.PollerSpec;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.dsl.RecipientListRouterSpec;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.file.FileNameGenerator;
import org.springframework.integration.file.remote.gateway.AbstractRemoteFileOutboundGateway;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.integration.sftp.gateway.SftpOutboundGateway;
import org.springframework.integration.sftp.outbound.SftpMessageHandler;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

//@Configuration
@Slf4j
public class SftpConfig implements InitializingBean {

    @Value("#{'${sftp.config.services}'.split(',')}")
    private List<String> sftpServices;

    @Autowired
    IntegrationFlowContext integrationFlowContext;

    @Autowired
    ApplicationContext context;

    @Autowired
    Environment env;

    //@Bean
    public SessionFactory sftpSessionFactory(String serviceName) {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory(true);
        factory.setHost(CommonUtil.getProperty(env, serviceName + ".sftp.host.name"));
        factory.setPort(Integer.parseInt(CommonUtil.getProperty(env, serviceName + ".sftp.port")));
        factory.setUser(CommonUtil.getProperty(env, serviceName + ".sftp.username"));
        factory.setPassword(CommonUtil.getProperty(env, serviceName + ".sftp.pwd"));

        factory.setAllowUnknownKeys(true);
        return new CachingSessionFactory<>(factory);

    }

    public TaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Is the minimum number of threads that remain active at any given point of time
        executor.setCorePoolSize(3);
        // The maxPoolSize relies on queueCapacity because ThreadPoolTaskExecutor
        // creates a new thread only if the number of items in the queue exceeds queue capacity.
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(3);
        executor.setThreadNamePrefix("sftp-th-exec");
        // Incase of 'Q' is full, we have to delegate the task to the caller thread to run the task.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }

    public GenericTransformer<Message,Message> transform = (msg) -> {
        log.info("[SFTP]-Processing file[{}] ", msg.getHeaders().get("file_originalFile"));

        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("[SFTP]-Processed file[{}] ", msg.getHeaders().get("file_originalFile"));
        return MessageBuilder.fromMessage(msg).build();
    };

    @Override
    public void afterPropertiesSet() throws Exception {
        PollerSpec pollerSpec = Pollers.cron("0/5 * * * * *").maxMessagesPerPoll(3);

        sftpServices.forEach( service -> {
            log.info("Initalizing {} sftp config service ", service);
            SessionFactory sessionFactory = sftpSessionFactory(service);
            IntegrationFlow flow = IntegrationFlow.from(
                            Sftp.inboundAdapter(sessionFactory)
                                    .preserveTimestamp(true)
                                    .remoteDirectory(CommonUtil.getProperty(env, service + ".inbound.dir"))
                                    .localDirectory(new File("./local/" + service + "/inbound/"))
                                    .autoCreateLocalDirectory(true)
                                    .patternFilter("*.*")
                                    .deleteRemoteFiles(false),
                            e -> e.poller(pollerSpec))
                    .enrichHeaders(headerEnricherSpec -> {
                        headerEnricherSpec.header("serviceName", service);
                        headerEnricherSpec.header("remote_dir", CommonUtil.getProperty(env,service + ".outbound.dir"));
                        headerEnricherSpec.header("sourceFilePath", "/sftp/inbound/AAA000008167464.xml");
                        headerEnricherSpec.header("destinationFilePath", "/sftp/outbound/AAA000008167464.xml");

                    })
                    .channel(MessageChannels.executor(threadPoolTaskExecutor()))
                    .channel(inboundFileChannel())
                    .handle(context.getBean(service + "ProcessorService"), "processMessage")
                    //.transform(transform)
                    .routeToRecipients(routerConfigurer(sessionFactory, service))
                    .get();

            IntegrationFlowContext.IntegrationFlowRegistration registration = this.integrationFlowContext.registration(flow)
                    .register();
            log.info("Initalized  {} sftp config service ", service);

        });
        //return flow;
    }

    public Consumer<RecipientListRouterSpec> routerConfigurer(SessionFactory sftpSessionFactory, String serviceName) {
        Consumer<RecipientListRouterSpec> routerConfigurer = (routeConfig) -> {
            routeConfig.applySequence(true)
                    .recipientFlow(sftpOutboundFlow(sftpSessionFactory,serviceName))
                    .recipient(archiveChannel(),"headers['" + CommonUtil.MOVE_TO_ARCHIEVE_FOLDER_ENABLED + "']!=null && headers['"+ CommonUtil.MOVE_TO_ARCHIEVE_FOLDER_ENABLED+ "']")
                    .recipient(sftpStatisticsFootPrintChannel());
        };

        return routerConfigurer;
    }

    public SftpRemoteFileTemplate template(SessionFactory sftpSessionFactory) {
        return new SftpRemoteFileTemplate(sftpSessionFactory);
    }

    public IntegrationFlow sftpOutboundFlow(SessionFactory sftpSessionFactory, String serviceName) {
        String mvCommand = "'/sftp/inbound/' + headers['file_name'] + ' /sftp/outbound/' + headers['file_name']";

        SftpOutboundGateway ftpOutboundGateway =
                new SftpOutboundGateway(sftpSessionFactory, "rm", "headers[''] 'my_remote_dir/'");
        //ftpOutboundGateway.
        ftpOutboundGateway.setOutputChannelName("lsReplyChannel");

        return IntegrationFlow.from(toSftpChannel())
                .filter("headers['" + CommonUtil.MOVE_TO_OUTBOUND_FOLDER_ENABLED + "'] !=null && headers['" + CommonUtil.MOVE_TO_OUTBOUND_FOLDER_ENABLED + "']")
                //.filter((GenericSelector<Message<File>>) msg -> isHeaderExist(msg, CommonUtil.MOVE_TO_OUTBOUND_FOLDER_ENABLED))
                .intercept(new ChannelInterceptor() {
                    public Message<?> preSend(Message<?> msg, MessageChannel channel) {
                        log.info("SFTP - [{}] - Uploading file via sftpOutboundFlow -{}", msg.getHeaders().get("serviceName"), msg);
                        return msg;
                    }
                    public void postSend(Message<?> msg, MessageChannel channel, boolean sent) {
                        log.info("SFTP - [{}] - Uploaded  file via sftpOutboundFlow -{}", msg.getHeaders().get("serviceName"), msg);
                    }
                })
                //.handle(Sftp.outboundGateway(sftpSessionFactory(), "mv", "'/source-directory/' + headers['file_name'] + ' /destination-directory/' + headers['file_name']")
               /* .handle(Sftp.outboundAdapter(sftpSessionFactory)
                                .remoteDirectory(CommonUtil.getProperty(env, serviceName + ".outbound.dir"))
                                .autoCreateDirectory(true))*/
                .handle(Sftp.outboundGateway(sftpSessionFactory, AbstractRemoteFileOutboundGateway.Command.MV,
                                "headers['sourceFilePath'] + ' ' + headers['destinationFilePath']")
                        .remoteDirectoryExpression(""))
                        //.remoteDirectoryExpression("headers['remote_dir']"))
                        //Sftp.outboundGateway(sftpSessionFactory, "mv", mvCommand))
                        //.remoteDirectoryFunction( msg -> CommonUtil.getProperty(env, serviceName + ".outbound.dir")))
                        //.remoteDirectoryExpression(CommonUtil.getProperty(env, serviceName + ".outbound.dir")))
                        //.fileNameGenerator(message -> message.getHeaders().get(FileHeaders.FILENAME, String.class)))
                .get();
    }

    private boolean isHeaderExist(Message<File> msg, String headerName) {
        return msg.getHeaders().get(headerName)!=null && Boolean.valueOf(msg.getHeaders().get(headerName).toString());
    }

    private boolean isImportantFile(Message<File> message) {
        String fileName = message.getPayload().getName();
        // Condition: Upload files whose names start with "important"
        return fileName.startsWith("important");
    }

    public MessageHandler handler(SessionFactory sftpSessionFactory) {
        SftpMessageHandler handler = new SftpMessageHandler(sftpSessionFactory);
        handler.setRemoteDirectoryExpression(new LiteralExpression("/outbound"));
        handler.setAutoCreateDirectory(true);
        handler.setFileNameGenerator(new FileNameGenerator() {
            @Override
            public String generateFileName(Message<?> message) {
                return ((File) message.getPayload()).getName();
            }
        });
        return handler;
    }


    public MessageChannel toSftpChannel() {
        DirectChannel channel =  new DirectChannel();
        channel.subscribe((msg) -> {
            log.info("SFTP - [{}] - toSftpChannel -{}", msg.getHeaders().get("serviceName"), msg);
        });
        return channel;
    }

    public MessageChannel archiveChannel() {
        DirectChannel channel =  new DirectChannel();
        channel.subscribe((msg) -> {
            log.info("SFTP - [{}] - archiveChannel -{}", msg.getHeaders().get("serviceName"), msg);
        });
        return channel;
    }

    public MessageChannel sftpStatisticsFootPrintChannel() {
        DirectChannel channel =  new DirectChannel();
        channel.subscribe((msg) -> {
            log.info("SFTP - [{}] - sftpStatisticsFootPrintChannel -{}", msg.getHeaders().get("serviceName"), msg);
        });
        return channel;
    }

    public MessageChannel inboundFileChannel() {
        ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel(Executors.newFixedThreadPool(5));
        channel.addInterceptor(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                String fileName = String.valueOf(message.getHeaders().get("file_name"));
                log.info("[SFTP]-[{}]-Pre Processor Audit file[{}] ",message.getHeaders().get("serviceName"),
                        message.getHeaders().get("file_originalFile"));
                return message;
            }
        });
        return channel;
    }


    //@Bean
    public MessageHandler handler() {
        return message -> {
            log.info("[SFTP]-Post processor Audit file[{}] ", message.getHeaders().get("file_originalFile"));
        };
    }

    //@Bean
    public MessageChannel channels() {
        DirectChannel channel =  new DirectChannel();
        channel.subscribe((msg) -> {
            log.info("channel -{}", msg);
        });
        return channel;
    }

}
