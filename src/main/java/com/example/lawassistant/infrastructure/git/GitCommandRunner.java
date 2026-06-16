package com.example.lawassistant.infrastructure.git;

import java.nio.file.Path;
import java.util.List;

public interface GitCommandRunner {

    String run(Path workingDirectory, List<String> command);
}
