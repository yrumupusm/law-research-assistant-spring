package com.example.lawassistant.infrastructure.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProcessGitCommandRunner implements GitCommandRunner {

    @Override
    public String run(Path workingDirectory, List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GitCommandException("git command failed: " + String.join(" ", command) + "\n" + output);
            }
            return output;
        } catch (IOException ex) {
            throw new GitCommandException("failed to run git command: " + String.join(" ", command), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GitCommandException("git command interrupted: " + String.join(" ", command), ex);
        }
    }
}
