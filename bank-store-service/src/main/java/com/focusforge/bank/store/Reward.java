package com.focusforge.bank.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rewards")
public class Reward {

    @Id
    private UUID id;

    /** null = global (admin-managed) reward. */
    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String emoji;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Optimistic lock. Two players redeeming the last unit both read stock=1;
     * the first commit bumps the version, the second UPDATE ... WHERE version=?
     * matches 0 rows and fails, so stock can never be oversold.
     */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Reward() {
    }

    public Reward(UUID ownerUserId, String name, String description, String emoji, int price, int stock) {
        this.id = UUID.randomUUID();
        this.ownerUserId = ownerUserId;
        this.createdAt = Instant.now();
        update(name, description, emoji, price, stock);
    }

    public void update(String name, String description, String emoji, int price, int stock) {
        this.name = name.strip();
        this.description = description == null ? "" : description.strip();
        this.emoji = emoji == null || emoji.isBlank() ? "🎁" : emoji.strip();
        this.price = price;
        this.stock = stock;
    }

    public void decrementStock() {
        if (stock <= 0) {
            throw new IllegalStateException("out of stock");
        }
        stock--;
    }

    public void deactivate() {
        active = false;
    }

    public boolean isGlobal() {
        return ownerUserId == null;
    }

    public boolean isOwnedBy(UUID userId) {
        return userId.equals(ownerUserId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getEmoji() {
        return emoji;
    }

    public int getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public boolean isActive() {
        return active;
    }
}
