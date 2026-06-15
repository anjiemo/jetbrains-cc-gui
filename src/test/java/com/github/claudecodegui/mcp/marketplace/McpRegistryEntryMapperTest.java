package com.github.claudecodegui.mcp.marketplace;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class McpRegistryEntryMapperTest {

    private final Gson gson = new Gson();

    private final McpMarketplaceSource source = new McpMarketplaceSource(
        "official-registry",
        "Official MCP Registry",
        McpMarketplaceSource.SourceType.REGISTRY,
        "https://registry.modelcontextprotocol.io",
        true
    );

    private McpInstallOption firstOption(String envelopeJson) {
        JsonObject envelope = gson.fromJson(envelopeJson, JsonObject.class);
        McpMarketplaceEntry entry = McpRegistryEntryMapper.fromRegistryObject(envelope, source);
        List<McpInstallOption> options = entry.getInstallOptions();
        Assert.assertFalse("expected at least one install option", options.isEmpty());
        return options.get(0);
    }

    @Test
    public void awsProxyRendersEndpointAndNamedArgsWithPlaceholders() {
        // Mirrors the live AWS ECS proxy entry: uvx + required SigV4 endpoint + named flags.
        String envelope = "{\"server\":{"
            + "\"name\":\"io.github.aws/mcp-proxy-for-aws\","
            + "\"packages\":[{"
            + "  \"registryType\":\"pypi\",\"identifier\":\"mcp-proxy-for-aws\",\"version\":\"1.1.6\","
            + "  \"runtimeHint\":\"uvx\",\"transport\":{\"type\":\"stdio\"},"
            + "  \"packageArguments\":["
            + "    {\"type\":\"positional\",\"value\":\"https://ecs-mcp.us-east-1.api.aws/mcp\"},"
            + "    {\"type\":\"named\",\"name\":\"--service\",\"value\":\"ecs-mcp\"},"
            + "    {\"type\":\"named\",\"name\":\"--profile\",\"value\":\"{profile}\"},"
            + "    {\"type\":\"named\",\"name\":\"--region\",\"value\":\"us-east-1\"}"
            + "  ]"
            + "}]"
            + "},\"_meta\":{}}";

        McpInstallOption option = firstOption(envelope);
        Assert.assertEquals("uvx", option.getCommand());
        Assert.assertEquals("stdio", option.getType());
        Assert.assertEquals(List.of(
            "mcp-proxy-for-aws@1.1.6",
            "https://ecs-mcp.us-east-1.api.aws/mcp",
            "--service", "ecs-mcp",
            "--profile", "{profile}",
            "--region", "us-east-1"
        ), option.getArgs());
    }

    @Test
    public void missingValueFallsBackToValueHintPlaceholder() {
        String envelope = "{\"server\":{\"name\":\"x\",\"packages\":[{"
            + "\"registryType\":\"pypi\",\"identifier\":\"p\",\"runtimeHint\":\"uvx\","
            + "\"packageArguments\":[{\"type\":\"positional\",\"valueHint\":\"endpoint_url\"}]"
            + "}]},\"_meta\":{}}";
        McpInstallOption option = firstOption(envelope);
        Assert.assertEquals(List.of("p", "{endpoint_url}"), option.getArgs());
    }

    @Test
    public void namedFlagWithoutValueRendersOnlyName() {
        String envelope = "{\"server\":{\"name\":\"x\",\"packages\":[{"
            + "\"registryType\":\"pypi\",\"identifier\":\"p\",\"runtimeHint\":\"uvx\","
            + "\"packageArguments\":[{\"type\":\"named\",\"name\":\"--verbose\"}]"
            + "}]},\"_meta\":{}}";
        McpInstallOption option = firstOption(envelope);
        Assert.assertEquals(List.of("p", "--verbose"), option.getArgs());
    }

    @Test
    public void runtimeArgumentsPrecedePackage() {
        String envelope = "{\"server\":{\"name\":\"x\",\"packages\":[{"
            + "\"registryType\":\"pypi\",\"identifier\":\"p\",\"version\":\"2.0\",\"runtimeHint\":\"uvx\","
            + "\"runtimeArguments\":[{\"type\":\"named\",\"name\":\"--from\",\"value\":\"p@2.0\"}],"
            + "\"packageArguments\":[{\"type\":\"positional\",\"value\":\"go\"}]"
            + "}]},\"_meta\":{}}";
        McpInstallOption option = firstOption(envelope);
        Assert.assertEquals(List.of("--from", "p@2.0", "p@2.0", "go"), option.getArgs());
    }

    @Test
    public void environmentVariablesAreRenderedWithPlaceholders() {
        String envelope = "{\"server\":{\"name\":\"x\",\"packages\":[{"
            + "\"registryType\":\"pypi\",\"identifier\":\"p\",\"runtimeHint\":\"uvx\","
            + "\"environmentVariables\":["
            + "  {\"name\":\"AWS_REGION\",\"value\":\"us-east-1\"},"
            + "  {\"name\":\"AWS_PROFILE\"}"
            + "]"
            + "}]},\"_meta\":{}}";
        McpInstallOption option = firstOption(envelope);
        Assert.assertEquals("us-east-1", option.getEnv().get("AWS_REGION"));
        Assert.assertEquals("{aws_profile}", option.getEnv().get("AWS_PROFILE"));
    }

    @Test
    public void transportTypeIsTakenFromPackageNotHardcoded() {
        String envelope = "{\"server\":{\"name\":\"x\",\"packages\":[{"
            + "\"registryType\":\"npm\",\"identifier\":\"p\",\"runtimeHint\":\"npx\","
            + "\"transport\":{\"type\":\"streamable-http\"}"
            + "}]},\"_meta\":{}}";
        McpInstallOption option = firstOption(envelope);
        Assert.assertEquals("http", option.getType());
    }

    @Test
    public void npmWithoutRuntimeArgumentsKeepsDashYDefault() {
        String envelope = "{\"server\":{\"name\":\"x\",\"packages\":[{"
            + "\"registryType\":\"npm\",\"identifier\":\"@scope/pkg\",\"version\":\"1.0\""
            + "}]},\"_meta\":{}}";
        McpInstallOption option = firstOption(envelope);
        Assert.assertEquals("npx", option.getCommand());
        Assert.assertEquals(List.of("-y", "@scope/pkg@1.0"), option.getArgs());
    }
}
