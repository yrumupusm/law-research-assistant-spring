package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.lawassistant.dto.SyncSourceRequest;
import com.example.lawassistant.domain.entity.SyncState;
import com.example.lawassistant.infrastructure.git.GitCommandException;
import com.example.lawassistant.infrastructure.git.GitCommandRunner;
import com.example.lawassistant.repository.SyncStateRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceSyncServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void syncClonesWhenTargetDoesNotExist() {
        FakeGitCommandRunner git = new FakeGitCommandRunner();
        SyncStateRepository syncStateRepository = syncStateRepository();
        SourceSyncService service = new SourceSyncService(git, null, syncStateRepository, "", null, "main");

        Path target = tempDir.resolve("laws");
        var response = service.sync(new SyncSourceRequest(
                "https://example.test/legal-data.git",
                target.toString(),
                "main",
                false,
                null
        ));

        assertThat(response.action()).isEqualTo("CLONED");
        assertThat(response.commitHash()).isEqualTo("abc123");
        assertThat(git.commands()).contains(
                "git clone --depth 1 --branch main https://example.test/legal-data.git " + target.toAbsolutePath().normalize()
        );
        assertThat(git.commands()).contains("git rev-parse HEAD");
        verify(syncStateRepository).save(any(SyncState.class));
    }

    @Test
    void syncPullsWhenTargetIsGitRepository() throws Exception {
        FakeGitCommandRunner git = new FakeGitCommandRunner();
        SourceSyncService service = new SourceSyncService(git, null, syncStateRepository(), "https://example.test/legal-data.git", null, "main");

        Path target = tempDir.resolve("laws");
        Files.createDirectories(target.resolve(".git"));

        var response = service.sync(new SyncSourceRequest(
                null,
                target.toString(),
                "develop",
                false,
                null
        ));

        assertThat(response.action()).isEqualTo("PULLED");
        assertThat(git.commands()).containsExactly(
                "git fetch --prune origin",
                "git checkout develop",
                "git pull --ff-only origin develop",
                "git rev-parse HEAD"
        );
    }

    @Test
    void syncRejectsNonGitNonEmptyDirectory() throws Exception {
        FakeGitCommandRunner git = new FakeGitCommandRunner();
        SourceSyncService service = new SourceSyncService(git, null, syncStateRepository(), "https://example.test/legal-data.git", null, "main");

        Path target = tempDir.resolve("laws");
        Files.createDirectories(target);
        Files.writeString(target.resolve("readme.txt"), "local file");

        assertThatThrownBy(() -> service.sync(new SyncSourceRequest(
                null,
                target.toString(),
                "main",
                false,
                null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a git repository");

        assertThat(git.commands()).isEmpty();
    }

    @Test
    void syncRecordsForcePushAndStopsWhenPreviousCommitIsNotAncestor() throws Exception {
        FakeGitCommandRunner git = new FakeGitCommandRunner("git merge-base --is-ancestor old123 origin/main");
        SyncState state = SyncState.empty();
        state.markSynced("old123");
        SyncStateRepository syncStateRepository = syncStateRepository(state);
        SourceSyncService service = new SourceSyncService(
                git,
                null,
                syncStateRepository,
                "https://example.test/legal-data.git",
                null,
                "main"
        );

        Path target = tempDir.resolve("laws");
        Files.createDirectories(target.resolve(".git"));

        assertThatThrownBy(() -> service.sync(new SyncSourceRequest(
                null,
                target.toString(),
                "main",
                false,
                null
        ))).isInstanceOf(GitCommandException.class)
                .hasMessageContaining("history changed");

        var captor = forClass(SyncState.class);
        verify(syncStateRepository).save(captor.capture());
        assertThat(captor.getValue().getLastForcePushDetectedAt()).isNotNull();
        assertThat(git.commands()).doesNotContain("git pull --ff-only origin main");
    }

    private SyncStateRepository syncStateRepository() {
        return syncStateRepository(null);
    }

    private SyncStateRepository syncStateRepository(SyncState state) {
        SyncStateRepository repository = mock(SyncStateRepository.class);
        when(repository.findById(SyncState.SINGLETON_ID)).thenReturn(Optional.ofNullable(state));
        when(repository.save(any(SyncState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return repository;
    }

    private static final class FakeGitCommandRunner implements GitCommandRunner {

        private final List<String> commands = new ArrayList<>();
        private final String failingCommand;

        private FakeGitCommandRunner() {
            this(null);
        }

        private FakeGitCommandRunner(String failingCommand) {
            this.failingCommand = failingCommand;
        }

        @Override
        public String run(Path workingDirectory, List<String> command) {
            String joined = String.join(" ", command);
            commands.add(joined);
            if (joined.equals(failingCommand)) {
                throw new GitCommandException("forced failure");
            }
            if (command.equals(List.of("git", "rev-parse", "HEAD"))) {
                return "abc123";
            }
            return "";
        }

        private List<String> commands() {
            return commands;
        }
    }
}
