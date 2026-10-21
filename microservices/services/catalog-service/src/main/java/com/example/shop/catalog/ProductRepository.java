package com.example.shop.catalog;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Trava a linha ate o fim da transacao: duas reservas do mesmo produto nao se atropelam. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") long id);

    /** JOIN FETCH traz produto e categoria numa consulta so (evita o N+1). A listagem paginada faz o mesmo em ProductSpecs. */
    @Query("select p from Product p left join fetch p.category where p.id = :id")
    Optional<Product> findByIdWithCategory(@Param("id") long id);

    boolean existsByCategoryId(Long categoryId);
}
