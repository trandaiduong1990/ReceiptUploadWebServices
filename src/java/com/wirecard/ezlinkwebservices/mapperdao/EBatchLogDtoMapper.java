package com.wirecard.ezlinkwebservices.mapperdao;

import com.wirecard.ezlinkwebservices.dto.EBatchLogDto;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

@Component
public interface EBatchLogDtoMapper {

    int insert(EBatchLogDto record) throws SQLException;
}