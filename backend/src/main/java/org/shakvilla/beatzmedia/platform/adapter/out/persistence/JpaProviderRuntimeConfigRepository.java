package org.shakvilla.beatzmedia.platform.adapter.out.persistence;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.platform.application.port.out.ProviderRuntimeConfigRepository;
import org.shakvilla.beatzmedia.platform.domain.ProviderCapability;
import org.shakvilla.beatzmedia.platform.domain.ProviderCode;
import org.shakvilla.beatzmedia.platform.domain.ProviderRuntimeConfig;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class JpaProviderRuntimeConfigRepository implements ProviderRuntimeConfigRepository {
  private static final TypeReference<Map<String, String>> CONFIG_TYPE = new TypeReference<>() {};

  private final EntityManager em;
  private final ObjectMapper json;
  private final ProviderRuntimeConfigCipher cipher;

  @Inject
  public JpaProviderRuntimeConfigRepository(
      EntityManager em, ObjectMapper json, ProviderRuntimeConfigCipher cipher) {
    this.em = em;
    this.json = json;
    this.cipher = cipher;
  }

  @Override
  public Optional<ProviderRuntimeConfig> find(ProviderCapability capability, ProviderCode provider) {
    String key = key(capability, provider);
    ProviderRuntimeConfigEntity entity = em.find(ProviderRuntimeConfigEntity.class, key);
    if (entity == null) return Optional.empty();
    try {
      Map<String, String> values = json.readValue(cipher.decrypt(entity.encryptedValues), CONFIG_TYPE);
      return Optional.of(new ProviderRuntimeConfig(
          capability, provider, values, entity.version, entity.updatedAt, entity.updatedBy));
    } catch (IOException e) {
      throw new IllegalStateException("Could not read provider configuration", e);
    }
  }

  @Override
  public Optional<ProviderRuntimeConfig> saveIfVersion(
      ProviderCapability capability,
      ProviderCode provider,
      Map<String, String> values,
      long expectedVersion,
      Instant updatedAt,
      String updatedBy) {
    String key = key(capability, provider);
    String encrypted;
    try {
      encrypted = cipher.encrypt(json.writeValueAsString(values));
    } catch (IOException e) {
      throw new IllegalStateException("Could not encode provider configuration", e);
    }

    int changed;
    if (expectedVersion == 0) {
      changed = em.createNativeQuery(
              "INSERT INTO provider_runtime_config "
                  + "(config_key, encrypted_values, version, updated_at, updated_by) "
                  + "VALUES (:key, :encrypted, 1, :updatedAt, :updatedBy) "
                  + "ON CONFLICT (config_key) DO NOTHING")
          .setParameter("key", key)
          .setParameter("encrypted", encrypted)
          .setParameter("updatedAt", updatedAt)
          .setParameter("updatedBy", updatedBy)
          .executeUpdate();
    } else {
      changed = em.createNativeQuery(
              "UPDATE provider_runtime_config SET encrypted_values = :encrypted, "
                  + "version = version + 1, updated_at = :updatedAt, updated_by = :updatedBy "
                  + "WHERE config_key = :key AND version = :expectedVersion")
          .setParameter("encrypted", encrypted)
          .setParameter("updatedAt", updatedAt)
          .setParameter("updatedBy", updatedBy)
          .setParameter("key", key)
          .setParameter("expectedVersion", expectedVersion)
          .executeUpdate();
    }
    if (changed != 1) return Optional.empty();
    em.clear();
    return find(capability, provider);
  }

  @Override
  public boolean encryptionAvailable() {
    return cipher.available();
  }

  private static String key(ProviderCapability capability, ProviderCode provider) {
    return capability.wireValue() + ":" + provider.wireValue();
  }
}
