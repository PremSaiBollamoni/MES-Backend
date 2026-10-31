package com.antigravity.mes.email;

import java.util.List;

/** Shared HTML shell for every automated email this app sends (payroll, daily attendance,
 * daily production) - same WhatsApp-green branding, same card layout, same fade/pop/pulse
 * animations (Gmail's web client runs CSS @keyframes; other clients just show the final
 * static state, never anything broken). Built once here so the three report emails stay
 * visually consistent instead of drifting apart as separate copy-pasted HTML strings.
 *
 * The stat-card row and the optional attachment chip are built as plain string
 * concatenation, not inside a %-formatted text block - a stray literal "%" in dynamic
 * content (a label, a number) would otherwise silently break java.lang.String#formatted. */
public final class BrandedEmailTemplate {

    private BrandedEmailTemplate() {}

    public static final String PRIMARY = "#25D366";
    public static final String PRIMARY_DARK = "#075E54";
    public static final String ACCENT_LIGHT = "#DCF8C6";
    private static final String INK = "#1F2937";
    private static final String MUTED = "#6B7280";
    private static final String BORDER = "#E5E7EB";
    private static final String SURFACE = "#F8FAF9";

    public record StatCard(String emoji, String value, String label) {}

    public static String build(
            String eyebrow,
            String title,
            String overview,
            String infoLabel,
            String infoValue,
            List<StatCard> stats,
            String attachmentLabel,
            String footerFactoryName) {

        String statsRow = buildStatsRow(stats);
        String attachmentBlock = attachmentLabel == null ? "" : buildAttachmentBlock(attachmentLabel);

        String shell = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8">
                <style>
                  @keyframes popIn {
                    0%% { opacity:0; transform:scale(0.6); }
                    70%% { opacity:1; transform:scale(1.08); }
                    100%% { opacity:1; transform:scale(1); }
                  }
                  @keyframes fadeUp {
                    0%% { opacity:0; transform:translateY(14px); }
                    100%% { opacity:1; transform:translateY(0); }
                  }
                  @keyframes pulseGlow {
                    0%%, 100%% { box-shadow:0 0 0 0 rgba(37,211,102,0.45); }
                    50%% { box-shadow:0 0 0 8px rgba(37,211,102,0); }
                  }
                  .checkmark { animation: popIn 0.6s cubic-bezier(.34,1.56,.64,1) 0.1s both; }
                  .fade-1 { animation: fadeUp 0.6s ease-out 0.25s both; }
                  .fade-2 { animation: fadeUp 0.6s ease-out 0.4s both; }
                  .fade-3 { animation: fadeUp 0.6s ease-out 0.55s both; }
                  .attach-chip { animation: pulseGlow 2.2s ease-in-out 1.2s 3; border-radius:8px; }
                </style>
                </head>
                <body style="margin:0;padding:0;background:%s;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:%s;padding:32px 16px;">
                    <tr><td align="center">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#FFFFFF;border-radius:16px;overflow:hidden;border:1px solid %s;box-shadow:0 8px 30px rgba(7,94,84,0.08);">
                        <tr>
                          <td style="background:linear-gradient(135deg,%s,%s);padding:32px 28px 26px;text-align:center;">
                            <table cellpadding="0" cellspacing="0" style="margin:0 auto 14px;" class="checkmark">
                              <tr>
                                <td width="56" height="56" style="width:56px;height:56px;border-radius:50%%;background:#FFFFFF;text-align:center;vertical-align:middle;">
                                  <span style="color:%s;font-size:28px;font-weight:900;line-height:56px;">&#10003;</span>
                                </td>
                              </tr>
                            </table>
                            <div class="fade-1" style="color:#FFFFFF;font-size:11px;font-weight:700;letter-spacing:1.4px;text-transform:uppercase;opacity:0.85;">${EYEBROW}</div>
                            <div class="fade-1" style="color:#FFFFFF;font-size:22px;font-weight:700;margin-top:6px;">${TITLE}</div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:26px 28px 6px;">
                            <div class="fade-2" style="color:%s;font-size:13.5px;line-height:1.75;text-align:left;">${OVERVIEW}</div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:18px 28px 6px;">
                            <table width="100%%" cellpadding="0" cellspacing="0" class="fade-2" style="background:%s;border-radius:12px;border:1px solid %s;">
                              <tr>
                                <td style="padding:16px 18px;text-align:center;">
                                  <div style="color:%s;font-size:11px;font-weight:700;letter-spacing:0.8px;text-transform:uppercase;">${INFO_LABEL}</div>
                                  <div style="color:%s;font-size:16px;font-weight:700;margin-top:4px;">${INFO_VALUE}</div>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:12px 28px 6px;">${STATS_ROW}</td>
                        </tr>
                        ${ATTACHMENT_BLOCK}
                        <tr>
                          <td style="padding:16px 28px 26px;">
                            <div style="color:%s;font-size:12px;border-top:1px solid %s;padding-top:16px;text-align:center;">
                              Generated automatically by ${FOOTER_FACTORY}.
                            </div>
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                SURFACE, SURFACE, BORDER,
                PRIMARY, PRIMARY_DARK,
                PRIMARY_DARK,
                INK,
                SURFACE, BORDER, MUTED, INK,
                MUTED, BORDER);

        return shell
                .replace("${EYEBROW}", eyebrow)
                .replace("${TITLE}", title)
                .replace("${OVERVIEW}", overview)
                .replace("${INFO_LABEL}", infoLabel)
                .replace("${INFO_VALUE}", infoValue)
                .replace("${STATS_ROW}", statsRow)
                .replace("${ATTACHMENT_BLOCK}", attachmentBlock)
                .replace("${FOOTER_FACTORY}", footerFactoryName);
    }

    /** Equal-width cards in one row, however many there are - built as plain concatenation
     * so a stat label/value containing a literal "%" (an efficiency figure, say) can never
     * collide with String#formatted's own placeholder syntax. */
    private static String buildStatsRow(List<StatCard> stats) {
        int gapPercent = 4;
        int cellPercent = (100 - gapPercent * (stats.size() - 1)) / stats.size();

        StringBuilder cells = new StringBuilder();
        for (int i = 0; i < stats.size(); i++) {
            StatCard stat = stats.get(i);
            if (i > 0) {
                cells.append("<td width=\"").append(gapPercent).append("%\"></td>");
            }
            cells.append("""
                    <td width="%d%%" class="fade-3" style="background:%s;border-radius:12px;padding:16px;text-align:center;">
                      <div style="font-size:22px;">%s</div>
                      <div style="color:%s;font-size:20px;font-weight:800;margin-top:4px;">%s</div>
                      <div style="color:#FFFFFF;font-size:11px;font-weight:700;letter-spacing:0.6px;text-transform:uppercase;opacity:0.85;margin-top:2px;">%s</div>
                    </td>
                    """.formatted(cellPercent, PRIMARY_DARK, stat.emoji(), ACCENT_LIGHT, stat.value(), stat.label()));
        }

        return "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>" + cells + "</tr></table>";
    }

    private static String buildAttachmentBlock(String attachmentLabel) {
        return """
                <tr>
                  <td style="padding:0px 28px 10px;text-align:center;">
                    <table cellpadding="0" cellspacing="0" style="margin:0 auto;">
                      <tr>
                        <td class="attach-chip" style="background:%s;padding:11px 16px;">
                          <span style="color:#FFFFFF;font-size:13px;font-weight:700;">&#128206; %s</span>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
                """.formatted(PRIMARY_DARK, attachmentLabel);
    }
}
