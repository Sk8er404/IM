package org.com.code.im.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.com.code.im.utils.SnowflakeIdUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class User {

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long id;

  private String userName;
  private String password;
  private String email;
  private String avatar;
  private String bio;
  private String auth;
  private int locked;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;


  public User(long id, String userName, String avatar, String bio,String createdAt,String updatedAt) {
    this.id = id;
    this.userName = userName;
    this.avatar = avatar;
    this.bio = bio;
    this.createdAt = LocalDateTime.parse(createdAt);
    this.updatedAt = LocalDateTime.parse(updatedAt);
  }

  public User(String username, String password, String email,String... authorities) {
    this.id = SnowflakeIdUtil.userIdWorker.nextId();
    this.userName = username;
    this.password = password;
    this.email = email;
    List<String> authoritiesList = new ArrayList<>();
    for (String authority : authorities) {
      authoritiesList.add(authority);
    }
    auth = String.join(" ", authoritiesList);
  }

  public Map toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id != null ? id.toString() : null);
    map.put("userName", userName);
    map.put("email", email);
    map.put("avatar", avatar);
    map.put("bio", bio);
    map.put("auth", auth);
    map.put("locked", locked);
    map.put("createdAt", createdAt);
    map.put("updatedAt", updatedAt);
    return map;
  }
}
