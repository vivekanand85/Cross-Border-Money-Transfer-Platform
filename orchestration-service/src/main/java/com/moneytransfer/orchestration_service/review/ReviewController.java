package com.moneytransfer.orchestration_service.review;

import com.moneytransfer.orchestration_service.service.TransferOrchestrationService;
import com.moneytransfer.orchestration_service.statemachine.TransferState;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewQueueRepository reviewQueueRepository;
    private final TransferOrchestrationService transferOrchestrationService;

    @GetMapping("/pending")
    public ResponseEntity<List<ReviewQueueEntry>> pending() {
        return ResponseEntity.ok(reviewQueueRepository.findByStatus("PENDING"));
    }

    @PostMapping("/{entryId}/approve")
    public ResponseEntity<ReviewQueueEntry> approve(@PathVariable UUID entryId,
                                                      @RequestParam(defaultValue = "MANUAL_REVIEWER") String reviewedBy) {
        ReviewQueueEntry entry = reviewQueueRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("No review entry found with id=" + entryId));

        entry.setStatus("APPROVED");
        entry.setReviewedBy(reviewedBy);
        entry.setReviewedAt(OffsetDateTime.now());
        reviewQueueRepository.save(entry);
        transferOrchestrationService.transitionTo(entry.getTransferId(), TransferState.PAY_IN, reviewedBy,
                "Approved via manual review, entryId=" + entryId);

        return ResponseEntity.ok(entry);
    }

    @PostMapping("/{entryId}/decline")
    public ResponseEntity<ReviewQueueEntry> decline(@PathVariable UUID entryId,
                                                       @RequestParam(defaultValue = "MANUAL_REVIEWER") String reviewedBy,
                                                       @RequestParam(required = false) String reason) {
        ReviewQueueEntry entry = reviewQueueRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("No review entry found with id=" + entryId));

        entry.setStatus("DECLINED");
        entry.setReviewedBy(reviewedBy);
        entry.setReviewedAt(OffsetDateTime.now());
        reviewQueueRepository.save(entry);

        transferOrchestrationService.transitionTo(entry.getTransferId(), TransferState.FAILED, reviewedBy,
                reason != null ? reason : "Declined via manual review, entryId=" + entryId);

        return ResponseEntity.ok(entry);
    }
}