package com.iot.platform.system.service;

import com.iot.platform.system.dto.LoginDTO;
import com.iot.platform.system.vo.UserInfoVO;

public interface AuthService {

    UserInfoVO login(LoginDTO dto);

    UserInfoVO currentUser();

    void logout();
}
