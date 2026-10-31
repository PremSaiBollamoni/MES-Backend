package com.antigravity.mes.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs report generation in the background so the HTTP request returns at once. A model call
 * can take minutes, and a request held open that long times out on the phone while the report
 * still finishes on the server. The app polls {@link #status} until the job is DONE or FAILED. */
@Service
public class ReportJobService {

    private static final Logger log = LoggerFactory.getLogger(ReportJobService.class);

    public enum State { GENERATING, DONE, FAILED, NONE }

    public record JobKey(Long factoryId, ReportType type, LocalDate date, PeriodType period) {}

    public record JobView(State state, ReportSummaryResponse report, String error) {}

    private static final class Job {
        volatile State state = State.GENERATING;
        volatile ReportSummaryResponse report;
        volatile String error;

        JobView view() {
            return new JobView(state, report, error);
        }
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<JobKey, Job> jobs = new ConcurrentHashMap<>();

    @Autowired
    private ReportGenerationService reportGenerationService;

    @Autowired
    private ClaudeReportService claudeReportService;

    /** Starts generating unless one is already running for this key. Fails at once if AI reports are not set up. */
    public synchronized JobView start(JobKey key, boolean force, Long employeeId, boolean isSuperAdmin) {
        Job running = jobs.get(key);
        if (running != null && running.state == State.GENERATING) return running.view();
        if (!claudeReportService.isEnabled()) {
            throw new IllegalStateException("AI reports are not set up - choose a report model in AI settings");
        }
        Job job = new Job();
        jobs.put(key, job);
        executor.submit(() -> run(key, job, force, employeeId, isSuperAdmin));
        return job.view();
    }

    /** The current state for this key. A report that exists but has no job here is reported as DONE. */
    public JobView status(JobKey key) {
        Job job = jobs.get(key);
        if (job != null) return job.view();
        return reportGenerationService.findExisting(key.type(), key.date(), key.period(), key.factoryId())
                .map(report -> new JobView(State.DONE, report, null))
                .orElse(new JobView(State.NONE, null, null));
    }

    private void run(JobKey key, Job job, boolean force, Long employeeId, boolean isSuperAdmin) {
        try {
            job.report = reportGenerationService.generate(
                    key.type(), key.date(), key.period(), force, employeeId, key.factoryId(), isSuperAdmin);
            job.state = State.DONE;
        } catch (Exception e) {
            log.error("Background report generation failed for {} {}", key.type(), key.date(), e);
            job.error = e.getMessage() != null ? e.getMessage() : "Report generation failed";
            job.state = State.FAILED;
        }
    }
}
