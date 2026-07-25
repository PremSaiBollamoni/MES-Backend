package com.antigravity.mes.model;

import jakarta.persistence.*;

/** A post-sewing pipeline zone (FGS/WSH/IRN-R/QCV/IRN-F/PKG/RTM) - PALMS §22. Seed data. */
@Entity
@Table(name = "stock_zones")
public class StockZone {

    @Id
    @Column(name = "zone_code", length = 10)
    private String zoneCode;

    @Column(name = "zone_name", length = 100, nullable = false)
    private String zoneName;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "max_alert_pieces")
    private Integer maxAlertPieces;

    public String getZoneCode() { return zoneCode; }
    public String getZoneName() { return zoneName; }
    public int getSequenceNo() { return sequenceNo; }
    public Integer getMaxAlertPieces() { return maxAlertPieces; }
}
