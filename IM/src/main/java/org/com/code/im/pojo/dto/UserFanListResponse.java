package org.com.code.im.pojo.dto;

import lombok.*;
import org.com.code.im.pojo.query.FanListPageQuery;
import org.com.code.im.pojo.UserFan;

import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserFanListResponse {
    private List<UserFan> userFanList;
    private FanListPageQuery fanListPageQuery;
}
