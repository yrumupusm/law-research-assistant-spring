package com.example.lawassistant.service;

import com.example.lawassistant.dto.IngestLocalResponse;
import com.example.lawassistant.dto.SyncSourceRequest;
import com.example.lawassistant.dto.SyncSourceResponse;
import com.example.lawassistant.domain.entity.SyncState;
import com.example.lawassistant.infrastructure.git.GitCommandException;
import com.example.lawassistant.infrastructure.git.GitCommandRunner;
import com.example.lawassistant.repository.SyncStateRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SourceSyncService {

    private final GitCommandRunner gitCommandRunner;
    private final LocalLawIngestionService localLawIngestionService;
    private final SyncStateRepository syncStateRepository;
    private final String defaultRepoUrl;
    private final Path defaultLocalDir;
    private final String defaultBranch;

    public SourceSyncService(
            GitCommandRunner gitCommandRunner,
            LocalLawIngestionService localLawIngestionService,
            SyncStateRepository syncStateRepository,
            @Value("${app.source.repo-url:}") String defaultRepoUrl,
            @Value("${app.source.local-dir:}") String defaultLocalDir,
            @Value("${app.source.branch:main}") String defaultBranch
    ) {
        this.gitCommandRunner = gitCommandRunner;
        this.localLawIngestionService = localLawIngestionService;
        this.syncStateRepository = syncStateRepository;
        this.defaultRepoUrl = defaultRepoUrl == null ? "" : defaultRepoUrl.strip();
        this.defaultLocalDir = defaultLocalDir == null || defaultLocalDir.isBlank() ? null : Path.of(defaultLocalDir);
        this.defaultBranch = defaultBranch == null || defaultBranch.isBlank() ? "main" : defaultBranch.strip();
    }

    public SyncSourceResponse sync(SyncSourceRequest request) {
        String repoUrl = textOrDefault(request == null ? null : request.repoUrl(), defaultRepoUrl);
        Path localDir = pathOrDefault(request == null ? null : request.localDir(), defaultLocalDir);
        String branch = textOrDefault(request == null ? null : request.branch(), defaultBranch);
        boolean ingestAfterSync = request != null && Boolean.TRUE.equals(request.ingestAfterSync());

        if (repoUrl.isBlank()) {
            throw new IllegalArgumentException("repoUrl is required when app.source.repo-url is not configured.");
        }
        if (localDir == null) {
            throw new IllegalArgumentException("localDir is required when app.source.local-dir is not configured.");
        }

        Path target = localDir.toAbsolutePath().normalize();
        SyncState syncState = syncStateRepository.findById(SyncState.SINGLETON_ID)
                .orElseGet(SyncState::empty);
        String action = syncRepository(repoUrl, target, branch, syncState);
        String commitHash = gitCommandRunner.run(target, List.of("git", "rev-parse", "HEAD"));
        recordSyncState(syncState, commitHash.strip());

        IngestLocalResponse ingestion = null;
        if (ingestAfterSync) {
            var result = localLawIngestionService.ingest(target, request == null ? null : request.snapshotPrefix());
            ingestion = new IngestLocalResponse(
                    result.run().getId(),
                    result.run().getStatus(),
                    result.snapshotVersion(),
                    result.filesProcessed(),
                    result.filesFailed(),
                    result.lawsImported(),
                    result.articlesImported(),
                    result.indexedArticles(),
                    result.run().getErrorMessage(),
                    result.run().getStartedAt(),
                    result.run().getFinishedAt()
            );
        }

        return new SyncSourceResponse(action, repoUrl, target.toString(), branch, commitHash.strip(), ingestion);
    }

    private void recordSyncState(SyncState state, String commitHash) {
        state.markSynced(commitHash);
        syncStateRepository.save(state);
    }

    private String syncRepository(String repoUrl, Path target, String branch, SyncState syncState) {
        if (Files.isDirectory(target.resolve(".git"))) {
            gitCommandRunner.run(target, List.of("git", "fetch", "--prune", "origin"));
            verifyHistory(syncState, target, branch);
            gitCommandRunner.run(target, List.of("git", "checkout", branch));
            gitCommandRunner.run(target, List.of("git", "pull", "--ff-only", "origin", branch));
            return "PULLED";
        }

        if (Files.exists(target) && !isEmptyDirectory(target)) {
            throw new IllegalArgumentException("localDir exists but is not a git repository: " + target);
        }

        createParent(target);
        List<String> command = new ArrayList<>(List.of("git", "clone", "--depth", "1"));
        if (!branch.isBlank()) {
            command.add("--branch");
            command.add(branch);
        }
        command.add(repoUrl);
        command.add(target.toString());
        gitCommandRunner.run(target.getParent(), command);
        return "CLONED";
    }

    private void verifyHistory(SyncState syncState, Path target, String branch) {
        String previousCommit = syncState.getLastSyncedCommitSha();
        if (previousCommit == null || previousCommit.isBlank()) {
            return;
        }
        try {
            gitCommandRunner.run(target, List.of(
                    "git",
                    "merge-base",
                    "--is-ancestor",
                    previousCommit,
                    "origin/" + branch
            ));
        } catch (GitCommandException ex) {
            syncState.markForcePushDetected();
            syncStateRepository.save(syncState);
            throw new GitCommandException("source repository history changed; manual review required.", ex);
        }
    }

    private String textOrDefault(String requested, String defaultValue) {
        return requested == null || requested.isBlank() ? defaultValue : requested.strip();
    }

    private Path pathOrDefault(String requested, Path defaultValue) {
        return requested == null || requested.isBlank() ? defaultValue : Path.of(requested);
    }

    private boolean isEmptyDirectory(Path target) {
        if (!Files.isDirectory(target)) {
            return false;
        }
        try (var stream = Files.list(target)) {
            return stream.findAny().isEmpty();
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to inspect localDir: " + target, ex);
        }
    }

    private void createParent(Path target) {
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to create localDir parent: " + target, ex);
        }
    }
}
