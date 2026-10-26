package org.purpurmc.purpur;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.engine.discovery.DiscoverySelectors;

public class TestRunner {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: TestRunner <test-class-name>");
            System.exit(1);
        }

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(args[0]))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        System.out.println("==================================================");
        System.out.println("Tests run: " + summary.getTestsFoundCount() +
                ", Successful: " + summary.getTestsSucceededCount() +
                ", Failed: " + summary.getTestsFailedCount() +
                ", Aborted: " + summary.getTestsAbortedCount());
        System.out.println("==================================================");

        if (summary.getTestsFailedCount() > 0) {
            summary.printFailuresTo(new java.io.PrintWriter(System.err));
            System.exit(1);
        }
    }
}
