package com.example.BackendArchitectureLab.Service;

import java.util.UUID;

/**
 * UserGateway - 跨服務使用者驗證的抽象介面（DIP），
 * Service 層不應直接依賴具體 Feign Client，應依賴此介面。
 */
public interface IUserGateway {

    boolean existsUserById(UUID id);
}
