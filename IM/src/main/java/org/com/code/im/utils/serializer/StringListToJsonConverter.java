package org.com.code.im.utils.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;


@MappedJdbcTypes(JdbcType.VARCHAR)
@MappedTypes(List.class)
public class StringListToJsonConverter extends BaseTypeHandler<List<String>> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        try {
             String jsonValue = (parameter == null) ? null : objectMapper.writeValueAsString(parameter);
             ps.setString(i, jsonValue);
        } catch (JsonProcessingException e) {
            throw new SQLException("Error converting List<String> to JSON string", e);
        }
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    private List<String> parseJson(String json) throws SQLException {
        if (json == null || json.isEmpty() || "null".equalsIgnoreCase(json)) {
             return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
             System.err.println("Failed to parse JSON: " + json);
             throw new SQLException("Error parsing JSON string to List<String>: " + e.getMessage(), e);
        }
    }
}