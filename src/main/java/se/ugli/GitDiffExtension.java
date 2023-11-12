package se.ugli;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Runtime.getRuntime;
import static java.lang.String.join;
import static java.util.stream.Collectors.toList;

@Component(role = EventSpy.class)
public class GitDiffExtension extends AbstractEventSpy {
    @Requirement
    Logger LOG;

    @Override
    public void init(Context context) {
        try {
            Map<String, Object> data = context.getData();
            Object userPropertiesObj = data.get("userProperties");
            Map<String, Object> userProperties = (Map<String, Object>) userPropertiesObj;
            String defaultBranch = getDefaultBranch();
            LOG.info("Default branch is '" + defaultBranch + "'");
            List<String> diffClasses = getDiffClasses(defaultBranch);
            LOG.info("Diff: " + diffClasses);
            userProperties.put("git.diff.classes", join(",", diffClasses));
        } catch (Exception e) {
            LOG.fatalError(e.getMessage(), e);
        }
    }

    List<String> getDiffClasses(String defaultBranch) throws IOException, InterruptedException {
        String cmd = "git diff --name-status " + defaultBranch;
       return execCmd(cmd)
                .stream()
                .filter(s -> s.endsWith(".java"))
                .filter(s -> !s.startsWith("D "))
                .filter(s -> !s.contains("src/test/java"))
                .map(s -> s.substring(s.indexOf("src/main/java/") + "src/main/java/".length()))
                .map(s -> s.replace(".java", ".class"))
                .collect(toList());
    }

    String getDefaultBranch() throws IOException, InterruptedException {
        String cmd = "git symbolic-ref refs/remotes/origin/HEAD";
        List<String> output = execCmd(cmd);
        if (output.isEmpty())
            throw new RemoteException("Couldn't get default branch by executing: " + cmd);
        if (output.size() > 1)
            LOG.warn("Strange! Executing cmd '" + cmd + "' gives more than 1 row");
        return output.get(0);
    }

    List<String> execCmd(String cmd) throws IOException, InterruptedException {
        Process process = null;
        try {
            process = getRuntime().exec(cmd.split(" "));
            process.waitFor();
            return getProcessOutput(process);
        } finally {
            if (process != null)
                process.destroy();
        }
    }

    List<String> getProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            List<String> result = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null)
                result.add(line);
            return result;
        }
    }
}
