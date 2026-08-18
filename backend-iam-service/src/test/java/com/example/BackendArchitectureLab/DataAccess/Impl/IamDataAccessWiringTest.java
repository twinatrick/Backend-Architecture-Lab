package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IRoleDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IRoleFunctionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserRoleDataAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        UserDataAccessImpl.class,
        UserRoleDataAccessImpl.class,
        RoleDataAccessImpl.class,
        RoleFunctionDataAccessImpl.class,
        FunctionDataAccessImpl.class
})
@DisplayName("IAM DataAccess Spring Context Wiring 裝配測試")
class IamDataAccessWiringTest {

    private final IUserDataAccess userDataAccess;
    private final IUserRoleDataAccess userRoleDataAccess;
    private final IRoleDataAccess roleDataAccess;
    private final IRoleFunctionDataAccess roleFunctionDataAccess;
    private final IFunctionDataAccess functionDataAccess;

    @Autowired
    public IamDataAccessWiringTest(
            IUserDataAccess userDataAccess,
            IUserRoleDataAccess userRoleDataAccess,
            IRoleDataAccess roleDataAccess,
            IRoleFunctionDataAccess roleFunctionDataAccess,
            IFunctionDataAccess functionDataAccess) {
        this.userDataAccess = userDataAccess;
        this.userRoleDataAccess = userRoleDataAccess;
        this.roleDataAccess = roleDataAccess;
        this.roleFunctionDataAccess = roleFunctionDataAccess;
        this.functionDataAccess = functionDataAccess;
    }

    @Test
    @DisplayName("所有 DataAccess Bean 應由 Spring Context 正確完成建構子注入裝配")
    void allDataAccessBeansShouldBeWiredSuccessfully() {
        assertThat(userDataAccess).isNotNull();
        assertThat(userRoleDataAccess).isNotNull();
        assertThat(roleDataAccess).isNotNull();
        assertThat(roleFunctionDataAccess).isNotNull();
        assertThat(functionDataAccess).isNotNull();
    }
}
