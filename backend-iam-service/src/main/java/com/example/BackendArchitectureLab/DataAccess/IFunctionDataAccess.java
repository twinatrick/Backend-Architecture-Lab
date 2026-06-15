package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Dto.Vo.Search.FunctionSearchQuery;
import com.example.BackendArchitectureLab.Entity.Function;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access interface for Function entity operations.
 * Abstracts FunctionRepository operations for service layer.
 */
public interface IFunctionDataAccess {

    /**
     * Find all functions by their IDs.
     *
     * @param ids list of function UUIDs
     * @return list of functions matching the IDs
     */
    List<Function> findAllById(List<UUID> ids);

    /**
     * Find a function by its ID.
     *
     * @param id the function UUID
     * @return optional containing the function if found
     */
    Optional<Function> findById(UUID id);

    /**
     * Save a single function.
     *
     * @param function the function to save
     * @return the saved function
     */
    Function save(Function function);

    /**
     * Check if a function exists matching the given example.
     *
     * @param example the function example to match
     * @return true if a matching function exists
     */
    boolean exists(Example<Function> example);

    /**
     * Find all functions.
     *
     * @return list of all functions
     */
    List<Function> findAll();

    /**
     * Find all functions with sorting.
     *
     * @param sort the sort specification
     * @return sorted list of all functions
     */
    List<Function> findAll(Sort sort);

    /**
     * Delete a function.
     *
     * @param function the function to delete
     */
    void delete(Function function);

    /**
     * Save all given functions.
     *
     * @param functions list of functions to save
     * @return list of saved functions
     */
    List<Function> saveAll(List<Function> functions);

    /**
     * Delete all given functions.
     *
     * @param functions list of functions to delete
     */
    void deleteAll(List<Function> functions);

    /**
     * Find all functions by grand parent ID.
     *
     * @param grandParentIds list of grand parent IDs
     * @return list of matching functions
     */
    List<Function> findAllByGrandParentId(List<String> grandParentIds);

    /**
     * Find a function by name.
     *
     * @param name the function name
     * @return the function if found, null otherwise
     */
    Function findFunctionByName(String name);

    /**
     * Find functions by name and parent.
     *
     * @param name the function name
     * @param parent the parent ID
     * @return list of matching functions
     */
    List<Function> findFunctionByNameAndParent(String name, String parent);
    
    /**
     * 分頁查詢功能
     *
     * @param query 查詢參數
     * @return 分頁結果
     */
    Page<Function> searchFunctions(FunctionSearchQuery query);
}
