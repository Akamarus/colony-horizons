package dev.colonyhorizons.qa.outpost;
public record ProbeResult(String caseId, Outcome outcome, String evidence) {
    public enum Outcome { PASS, FAIL, BLOCKED }
}
