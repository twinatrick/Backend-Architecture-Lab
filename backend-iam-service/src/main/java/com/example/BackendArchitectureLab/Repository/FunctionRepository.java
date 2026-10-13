package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.Function;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FunctionRepository extends JpaRepository<Function, UUID>, JpaSpecificationExecutor<Function> {


    @Query("select f from Function f where f.parent IN ?1")
    List<Function> findAllByGrandParentId(List<String> grandParentId);

    @Query("select f from Function f where f.parent IN ?1")
    List<Function> getParent(List<String> parent);

    Function findFunctionByName(String name);

    @Query("select f from Function f where f.name = ?1 and f.parent = ?2")
    List<Function> findFunctionByNameAndParent(String name, String parent);

    @Query("SELECT f.id FROM Function f")
    List<UUID> findAllIds();

    @Query("SELECT f FROM Function f LEFT JOIN FETCH f.roleFunctions rf LEFT JOIN FETCH rf.role WHERE f.id = :id")
    Optional<Function> findByIdWithRoleFunctions(@Param("id") UUID id);

    @Query("""
            SELECT DISTINCT f FROM Function f 
            WHERE CAST(f.id AS string) IN (
                SELECT c.parent FROM Function c WHERE c.id IN :childIds
            ) 
            OR CAST(f.id AS string) IN (
                SELECT p.parent FROM Function p WHERE CAST(p.id AS string) IN (
                    SELECT c2.parent FROM Function c2 WHERE c2.id IN :childIds
                )
            )
            """)
    List<Function> findAllParentHierarchyByChildIds(@Param("childIds") List<UUID> childIds);
}
