package com.bd.blooddonerfinder.payload.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import javax.sql.DataSource;
@Data
@AllArgsConstructor
public class MailAttachment {
    private String attachmentName;
    private DataSource dataSource;
}
