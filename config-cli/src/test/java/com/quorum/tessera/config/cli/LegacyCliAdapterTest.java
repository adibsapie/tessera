package com.quorum.tessera.config.cli;

import com.quorum.tessera.config.Config;
import com.quorum.tessera.config.Peer;
import com.quorum.tessera.config.SslTrustMode;
import com.quorum.tessera.config.builder.ConfigBuilder;
import com.quorum.tessera.config.test.FixtureUtil;
import org.apache.commons.cli.CommandLine;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LegacyCliAdapterTest {

    private final ConfigBuilder builderWithValidValues = FixtureUtil.builderWithValidValues();

    private final LegacyCliAdapter instance = new LegacyCliAdapter();

    @Test
    public void help() throws Exception {

        CliResult result = instance.execute("--help");
        assertThat(result).isNotNull();
        assertThat(result.getConfig()).isNotPresent();
        assertThat(result.getStatus()).isEqualTo(0);

    }

    @Test
    public void noOptionsWithTomlFile() throws Exception {

        Path sampleFile = Paths.get(getClass().getResource("/sample.conf").toURI());
        Path configFile = Files.createTempFile("noOptions", ".txt");

        Files.write(configFile, Files.readAllBytes(sampleFile));

        CliResult result = instance.execute(configFile.toString());

        assertThat(result).isNotNull();
        assertThat(result.getConfig()).isPresent();
        assertThat(result.getStatus()).isEqualTo(0);

        Files.deleteIfExists(configFile);

    }

    @Test
    public void applyOverrides() throws Exception {

        String urlOverride = "http://junit.com:8989";
        int portOverride = 9999;
        String unixSocketFileOverride = "unixSocketFileOverride.ipc";

        List<Peer> overridePeers = Arrays.asList(new Peer("http://otherone.com:9188/other"), new Peer("http://yetanother.com:8829/other"));

        CommandLine commandLine = mock(CommandLine.class);

        when(commandLine.getOptionValue("url")).thenReturn(urlOverride);
        when(commandLine.getOptionValue("port")).thenReturn(String.valueOf(portOverride));
        when(commandLine.getOptionValue("socket")).thenReturn(unixSocketFileOverride);
        when(commandLine.getOptionValues("othernodes"))
                .thenReturn(
                        overridePeers.stream()
                                .map(Peer::getUrl)
                                .collect(Collectors.toList())
                                .toArray(new String[0])
                );

        when(commandLine.getOptionValue("storage")).thenReturn("sqlite:somepath");

        when(commandLine.getOptionValue("tlsservertrust")).thenReturn("whitelist");

        when(commandLine.getOptionValue("tlsclienttrust")).thenReturn("ca");

        when(commandLine.getOptionValue("tlsservercert")).thenReturn("tlsservercert.cert");
        when(commandLine.getOptionValue("tlsclientcert")).thenReturn("tlsclientcert.cert");

        when(commandLine.getOptionValues("publickeys"))
                .thenReturn(new String[]{"ONE", "TWO"});

        List<Path> privateKeyPaths = Arrays.asList(
                Files.createTempFile("applyOverrides1", ".txt"),
                Files.createTempFile("applyOverrides2", ".txt")
        );

        final byte[] privateKeyData = FixtureUtil.createLockedPrivateKey().toString().getBytes();
        for (Path p : privateKeyPaths) {
            Files.write(p, privateKeyData);
        }

        final String[] privateKeyPathStrings = privateKeyPaths.stream()
                .map(Path::toString)
                .collect(Collectors.toList())
                .toArray(new String[0]);

        when(commandLine.getOptionValues("privatekeys")).thenReturn(privateKeyPathStrings);

        final List<String> privateKeyPasswords = Arrays.asList("SECRET1", "SECRET2");

        final Path privateKeyPasswordFile = Files.createTempFile("applyOverridesPasswords", ".txt");
        Files.write(privateKeyPasswordFile, privateKeyPasswords);

        when(commandLine.getOptionValue("passwords"))
                .thenReturn(privateKeyPasswordFile.toString());

        Config result = LegacyCliAdapter.applyOverrides(commandLine, builderWithValidValues).build();

        assertThat(result).isNotNull();
        assertThat(result.getServerConfig().getHostName()).isEqualTo(urlOverride);
        assertThat(result.getServerConfig().getPort()).isEqualTo(portOverride);
        assertThat(result.getUnixSocketFile()).isEqualTo(Paths.get(unixSocketFileOverride));
        assertThat(result.getPeers()).containsExactly(overridePeers.toArray(new Peer[0]));
        assertThat(result.getKeys()).hasSize(2);
        assertThat(result.getJdbcConfig()).isNotNull();
        assertThat(result.getJdbcConfig().getUrl()).isEqualTo("jdbc:sqlite:somepath");

        assertThat(result.getServerConfig().getSslConfig().getServerTrustMode()).isEqualTo(SslTrustMode.WHITELIST);
        assertThat(result.getServerConfig().getSslConfig().getClientTrustMode()).isEqualTo(SslTrustMode.CA);

        assertThat(result.getServerConfig().getSslConfig().getClientKeyStore()).isEqualTo(Paths.get("tlsclientcert.cert"));

        assertThat(result.getServerConfig().getSslConfig().getServerKeyStore()).isEqualTo(Paths.get("tlsservercert.cert"));

        Files.deleteIfExists(privateKeyPasswordFile);
        for (Path privateKeyPath : privateKeyPaths) {
            Files.deleteIfExists(privateKeyPath);
        }

    }

    @Test
    public void applyOverridesNullValues() {

        Config expectedValues = builderWithValidValues.build();

        CommandLine commandLine = mock(CommandLine.class);

        Config result = LegacyCliAdapter.applyOverrides(commandLine, builderWithValidValues).build();

        assertThat(result).isNotNull();

        assertThat(result.getServerConfig().getHostName())
                .isEqualTo(expectedValues.getServerConfig().getHostName());

        assertThat(result.getServerConfig().getPort())
                .isEqualTo(expectedValues.getServerConfig().getPort());

        assertThat(result.getUnixSocketFile())
                .isEqualTo(expectedValues.getUnixSocketFile());

        assertThat(result.getPeers())
                .containsOnlyElementsOf(expectedValues.getPeers());

        assertThat(result.getJdbcConfig().getUrl()).isEqualTo("jdbc:bogus");

        assertThat(result.getServerConfig().getSslConfig().getServerTrustMode()).isEqualTo(SslTrustMode.TOFU);
        assertThat(result.getServerConfig().getSslConfig().getClientTrustMode()).isEqualTo(SslTrustMode.CA_OR_TOFU);
        assertThat(result.getServerConfig().getSslConfig().getClientKeyStore()).isEqualTo(Paths.get("sslClientKeyStorePath"));

        assertThat(result.getServerConfig().getSslConfig().getServerKeyStore()).isEqualTo(Paths.get("sslServerKeyStorePath"));
    }

}
