package io.github.gaojindeng.lock.demo.dubbo.provider.dao.mapper;

import io.github.gaojindeng.lock.demo.dubbo.provider.dao.entity.LockRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LockRecordMapper {

    @Insert("insert into lock_record(`key`,lock_type,server_name,thread_name,remark) values(" +
            "#{key},#{lockType},#{serverName},#{threadName},#{remark})")
    int insert(LockRecord entity);

    @Select("select * from lock_record where `key` = #{key} order by id asc")
    List<LockRecord> getByKey(String key);
}
