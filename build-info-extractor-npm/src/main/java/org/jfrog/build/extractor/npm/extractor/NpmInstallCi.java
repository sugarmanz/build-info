package org.jfrog.build.extractor.npm.extractor;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.util.Log;
import org.jfrog.build.extractor.clientConfiguration.ArtifactoryClientConfiguration;
import org.jfrog.build.extractor.clientConfiguration.ArtifactoryDependenciesClientBuilder;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryDependenciesClient;
import org.jfrog.build.extractor.npm.types.NpmProject;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.jfrog.build.extractor.packageManager.PackageManagerUtils.createArtifactoryClientConfiguration;

/**
 * @author Yahav Itzhak
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class NpmInstallCi extends NpmCommand {

    NpmBuildInfoExtractor buildInfoExtractor;
    List<String> commandArgs;
    boolean isCiCommand;

    /**
     * Run npm install or npm ci commands.
     *
     * @param clientBuilder        - Build Info client builder.
     * @param resolutionRepository - The repository it'll resolve from.
     * @param commandArgs          - Npm command args.
     * @param logger               - The logger.
     * @param path                 - Path to directory contains package.json or path to '.tgz' file.
     * @param env                  - Environment variables to use during npm execution.
     */
    public NpmInstallCi(ArtifactoryDependenciesClientBuilder clientBuilder, String resolutionRepository, String commandArgs, Log logger, Path path, Map<String, String> env, String module, boolean isCiCommand) {
        super(clientBuilder, resolutionRepository, logger, path, env);
        buildInfoExtractor = new NpmBuildInfoExtractor(clientBuilder, npmDriver, logger, module);
        this.commandArgs = StringUtils.isBlank(commandArgs) ? new ArrayList<>() : Arrays.asList(commandArgs.trim().split("\\s+"));
        this.isCiCommand = isCiCommand;
    }

    @Override
    public Build execute() {
        try (ArtifactoryDependenciesClient dependenciesClient = (ArtifactoryDependenciesClient) clientBuilder.build()) {
            client = dependenciesClient;
            validatePath();
            validateArtifactoryVersion();
            validateNpmVersion();
            validateRepoExists(client, repo, "Source repo must be specified");

            NpmProject npmProject = new NpmProject(commandArgs, repo, workingDir, isCiCommand);
            return buildInfoExtractor.extract(npmProject);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Allow running npm install using a new Java process.
     * Used only in Jenkins to allow running 'rtNpm install' in a docker container.
     */
    public static void main(String[] ignored) {
        try {
            ArtifactoryClientConfiguration clientConfiguration = createArtifactoryClientConfiguration();
            ArtifactoryDependenciesClientBuilder clientBuilder = new ArtifactoryDependenciesClientBuilder().setClientConfiguration(clientConfiguration, clientConfiguration.resolver);
            ArtifactoryClientConfiguration.PackageManagerHandler npmHandler = clientConfiguration.packageManagerHandler;
            NpmInstallCi npmInstall = new NpmInstallCi(clientBuilder,
                    clientConfiguration.resolver.getRepoKey(),
                    npmHandler.getArgs(),
                    clientConfiguration.getLog(),
                    Paths.get(npmHandler.getPath() != null ? npmHandler.getPath() : "."),
                    clientConfiguration.getAllProperties(),
                    npmHandler.getModule(),
                    clientConfiguration.npmHandler.isCiCommand());
            npmInstall.executeAndSaveBuildInfo(clientConfiguration);
        } catch (RuntimeException e) {
            ExceptionUtils.printRootCauseStackTrace(e, System.out);
            System.exit(1);
        }
    }
}