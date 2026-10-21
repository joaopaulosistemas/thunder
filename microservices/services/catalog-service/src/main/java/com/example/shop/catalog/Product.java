package com.example.shop.catalog;

import com.example.shop.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Produto. Exclusao LOGICA: o DELETE vira UPDATE deleted = true e o Hibernate esconde as linhas
 * excluidas de todas as consultas. Pedidos antigos continuam apontando para um produto que existiu.
 * A categoria e LAZY: so e lida do banco quando alguem a usa (consultas de listagem usam JOIN FETCH).
 */
@Entity
@Table(name = "products")
@SQLDelete(sql = "update products set deleted = true, updated_at = now(), version = version + 1 where id = ? and version = ?")
@SQLRestriction("deleted = false")
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Column(nullable = false)
    private int stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private boolean deleted;

    protected Product() {
    }

    public Product(String sku, String name, int priceCents, int stock) {
        this.sku = sku;
        this.name = name;
        this.priceCents = priceCents;
        this.stock = stock;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public int getStock() {
        return stock;
    }

    public Category getCategory() {
        return category;
    }

    public void update(String name, int priceCents, int stock, Category category) {
        this.name = name;
        this.priceCents = priceCents;
        this.stock = stock;
        this.category = category;
    }

    /** Baixa o estoque se houver saldo; devolve false (sem alterar) se nao houver. */
    public boolean reserve(int quantity) {
        if (quantity <= 0 || stock < quantity) {
            return false;
        }
        stock -= quantity;
        return true;
    }

    /** Devolve estoque reservado (compensacao da saga). */
    public void release(int quantity) {
        if (quantity > 0) {
            stock += quantity;
        }
    }
}
