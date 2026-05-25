package com.portfolioguard.portfolioguard.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "stocks")
@Data
@NoArgsConstructor
@AllArgsConstructor

public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String ticker;

    private double currentPrice;
    private double purchasePrice;
    private int quantity;
    private String sector;

    @Column(name = "portfolio_id", insertable = false, updatable = false)
    private String portfolioId;


    @Column(updatable = false)
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }
}
