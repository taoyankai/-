package com.hz.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.delivery.entity.Admin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminMapper extends BaseMapper<Admin> {

    @Select("SELECT * FROM t_admin WHERE username = #{username} AND deleted = 0 LIMIT 1")
    Admin selectByUsername(@Param("username") String username);
}
