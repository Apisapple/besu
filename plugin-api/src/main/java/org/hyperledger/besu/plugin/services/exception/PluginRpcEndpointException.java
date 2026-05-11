/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.plugin.services.exception;

import org.hyperledger.besu.plugin.services.rpc.RpcMethodError;

import org.jspecify.annotations.Nullable;

/** Base exception class for problems encountered in the RpcEndpointService. */
public class PluginRpcEndpointException extends RuntimeException {
  /** The error */
  private final RpcMethodError rpcMethodError;

  /** The data associated with the exception */
  private final @Nullable String data;

  /**
   * Constructs a new PluginRpcEndpointException exception with the specified error.
   *
   * @param rpcMethodError the error.
   */
  public PluginRpcEndpointException(final RpcMethodError rpcMethodError) {
    this(rpcMethodError, null);
  }

  /**
   * Constructs a new PluginRpcEndpointException exception with the specified error and message.
   *
   * @param rpcMethodError the error.
   * @param data the data associated with the exception that could be parsed to extract more
   *     information to return in the error response.
   */
  public PluginRpcEndpointException(
      final RpcMethodError rpcMethodError, final @Nullable String data) {
    this(rpcMethodError, data, null);
  }

  /**
   * Constructs a new PluginRpcEndpointException exception with the specified error, message and
   * cause.
   *
   * @param rpcMethodError the error.
   * @param data the data associated with the exception that could be parsed to extract more
   *     information to return in the error response.
   * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).
   *     (A {@code null} value is permitted, and indicates that the cause is nonexistent or
   *     unknown.)
   */
  public PluginRpcEndpointException(
      final RpcMethodError rpcMethodError,
      final @Nullable String data,
      final @Nullable Throwable cause) {
    super(rpcMethodError.getMessage(), cause);
    this.rpcMethodError = rpcMethodError;
    this.data = data;
  }

  /**
   * Get the error
   *
   * @return the error
   */
  public RpcMethodError getRpcMethodError() {
    return rpcMethodError;
  }

  /**
   * Get the data associated with the exception
   *
   * @return data as string, could be null.
   */
  public @Nullable String getData() {
    return data;
  }
}
