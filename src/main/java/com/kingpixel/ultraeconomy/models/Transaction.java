package com.kingpixel.ultraeconomy.models;

import com.kingpixel.ultraeconomy.database.TransactionType;
import lombok.Data;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 *
 * @author Carlos Varas Alonso - 17/12/2025 23:16
 */
@Data
public class Transaction {

  private UUID accountUUID;
  private String currency;
  private BigDecimal amount;
  private TransactionType type;
  private boolean processed;
  private Instant timestamp;


  // MONGODB
  private static final String FIELD_ACCOUNT_UUID = "account_uuid";
  private static final String FIELD_CURRENCY_ID = "currency_id";
  private static final String FIELD_AMOUNT = "amount";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_PROCESSED = "processed";
  private static final String FIELD_BACKUP_UUID = "uuid";

  public Transaction(UUID uuid, String currency, BigDecimal amount, TransactionType type, boolean processed,
                     Instant now) {
    this.accountUUID = uuid;
    this.currency = currency;
    this.amount = amount;
    this.type = type;
    this.processed = processed;
    this.timestamp = now;
  }

  public static Transaction fromDocument(Document doc) {
    UUID accountUUID = UUID.fromString(doc.getString(FIELD_ACCOUNT_UUID));
    String currency = doc.getString(FIELD_CURRENCY_ID);
    BigDecimal amount = doc.get(FIELD_AMOUNT, Decimal128.class).bigDecimalValue();
    TransactionType type = TransactionType.valueOf(doc.getString(FIELD_TYPE));
    boolean processed = doc.getBoolean(FIELD_PROCESSED);
    Instant timestamp = doc.containsKey("timestamp") ?
      doc.getDate("timestamp").toInstant() : Instant.now();

    return new Transaction(accountUUID, currency, amount, type, processed, timestamp);
  }

  public Document toDocument() {
    return new Document(FIELD_ACCOUNT_UUID, accountUUID.toString())
      .append(FIELD_CURRENCY_ID, currency)
      .append(FIELD_AMOUNT, new Decimal128(amount))
      .append(FIELD_TYPE, type.name())
      .append(FIELD_PROCESSED, processed)
      .append("timestamp", Date.from(Instant.now()));
  }
}
