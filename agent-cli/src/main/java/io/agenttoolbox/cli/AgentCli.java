package io.agenttoolbox.cli;

import io.agenttoolbox.common.config.EnvVarSecretProvider;
import io.agenttoolbox.core.AgentRunner;
import io.agenttoolbox.core.ToolRegistry;
import io.agenttoolbox.core.config.AgentConfig;
import io.agenttoolbox.core.config.ConfigLoader;
import io.agenttoolbox.core.model.AgentResponse;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "agent-toolbox", mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "LLM-powered agent for file storage operations")
public class AgentCli implements Callable<Integer> {

    @Option(names = {"-v", "--verbose"}, description = "Always use verbose output")
    boolean verbose;

    @Option(names = {"--model"}, description = "Override LLM model name")
    String model;

    @Option(names = {"--config"}, description = "Path to config YAML")
    Path configPath;

    @Parameters(description = "One-shot query (skip REPL)", arity = "0..*")
    String[] query;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AgentCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        AgentConfig config = ConfigLoader.loadFromClasspath();
        applyOverrides(config);
        VerbosityMode verbosityMode = verbose ? VerbosityMode.VERBOSE : VerbosityMode.AUTO;
        OutputFormatter formatter = new OutputFormatter(verbosityMode);
        ToolRegistry registry = new ToolRegistry();
        AgentRunner runner = new AgentRunner(config, new EnvVarSecretProvider(), registry);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("\nGoodbye.")));
        if (query != null && query.length > 0) {
            return runOneShot(runner, formatter, String.join(" ", query));
        }
        return runRepl(runner, formatter, config);
    }

    private int runOneShot(AgentRunner runner, OutputFormatter formatter, String input) {
        AgentResponse response = runner.chat(input);
        System.out.println(formatter.format(response));
        return response.success() ? 0 : 1;
    }

    private int runRepl(AgentRunner runner, OutputFormatter formatter, AgentConfig config) {
        String provider = config.getLlm().getProvider();
        String modelName = "gemini".equals(provider)
                ? config.getLlm().getGemini().getModel()
                : config.getLlm().getOllama().getModel();
        System.out.printf("%s v%s (%s: %s)%n",
                config.getAgent().getName(), config.getAgent().getVersion(),
                provider, modelName);
        System.out.println("Type your request, or 'help' for commands. 'quit' to exit.\n");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> "); System.out.flush();
                String line = reader.readLine();
                if (line == null || "quit".equalsIgnoreCase(line.trim()) || "exit".equalsIgnoreCase(line.trim())) break;
                String input = line.trim();
                if (input.isEmpty()) continue;
                if ("help".equalsIgnoreCase(input)) {
                    System.out.println("Available tools: " + runner.getToolNames());
                    System.out.println("Commands: help, clear, quit/exit");
                    System.out.println("Describe your file storage problem in natural language.\n");
                    continue;
                }
                if ("clear".equalsIgnoreCase(input)) {
                    runner.clearMemory();
                    System.out.println("Chat memory cleared.\n");
                    continue;
                }
                AgentResponse response = runner.chat(input);
                System.out.println(formatter.format(response) + "\n");
            }
        } catch (Exception e) { System.err.println("Error: " + e.getMessage()); return 1; }
        return 0;
    }

    private void applyOverrides(AgentConfig config) {
        if (model != null) {
            String provider = config.getLlm().getProvider();
            if ("gemini".equals(provider)) {
                config.getLlm().getGemini().setModel(model);
            } else {
                config.getLlm().getOllama().setModel(model);
            }
        }
        if (verbose) config.getOutput().setVerbosity("verbose");
    }
}
