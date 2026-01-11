# Besu App Controller Module Architecture Guide

## Overview

This document provides a detailed explanation of the `app/controller` module in the Hyperledger Besu project. The Controller module plays a critical role in initializing and managing the core components of a Besu node.

## Directory Structure

```
app/src/main/java/org/hyperledger/besu/controller/
├── BesuController.java                          # Main controller class
├── BesuControllerBuilder.java                   # Abstract builder class (1390 lines)
├── MainnetBesuControllerBuilder.java            # Builder for Mainnet
├── CliqueBesuControllerBuilder.java             # Builder for Clique consensus
├── IbftBesuControllerBuilder.java               # Builder for IBFT2 consensus
├── QbftBesuControllerBuilder.java               # Builder for QBFT consensus
├── IbftLegacyBesuControllerBuilder.java         # Builder for IBFT Legacy
├── MergeBesuControllerBuilder.java              # Builder for PoS Merge
├── TransitionBesuControllerBuilder.java         # Builder for PoW to PoS transition
├── ConsensusScheduleBesuControllerBuilder.java  # Builder for consensus migration schedule
├── PluginServiceFactory.java                    # Plugin service factory interface
├── NoopPluginServiceFactory.java                # Default plugin service factory
├── BftQueryPluginServiceFactory.java            # BFT query plugin service
├── CliqueQueryPluginServiceFactory.java         # Clique query plugin service
├── IbftQueryPluginServiceFactory.java           # IBFT query plugin service
└── MiningConfigurationOverrides.java            # Mining configuration override interface
```

## Key Components

### 1. BesuController

`BesuController` is the main controller that manages the core components of a Besu node.

#### Main Responsibilities:
- Protocol schedule management (ProtocolSchedule)
- Protocol context management (ProtocolContext)
- Ethereum protocol manager (EthProtocolManager)
- Synchronizer management
- Transaction pool (TransactionPool)
- Mining coordinator (MiningCoordinator)
- JSON-RPC method provisioning
- Resource cleanup (implements Closeable)

#### Key Fields:
```java
private final ProtocolSchedule protocolSchedule;         // Protocol version-specific rules
private final ProtocolContext protocolContext;           // Blockchain state and context
private final EthProtocolManager ethProtocolManager;     // P2P network management
private final Synchronizer synchronizer;                 // Block synchronization
private final TransactionPool transactionPool;           // Transaction memory pool
private final MiningCoordinator miningCoordinator;       // Block creation coordination
private final StorageProvider storageProvider;           // Data storage provider
private final TransactionSimulator transactionSimulator; // Transaction simulation
```

#### Lifecycle:
1. Creation through Builder pattern
2. Service initialization
3. Component management during node execution
4. Resource cleanup via `close()` method

### 2. BesuControllerBuilder (Abstract Class)

The abstract base class for all concrete builder classes, implementing the Builder pattern.

#### Design Patterns:
- **Builder Pattern**: Step-by-step handling of complex object creation
- **Template Method Pattern**: Common logic in abstract class, specialized logic in subclasses
- **Factory Pattern**: Creates appropriate builders based on consensus algorithm

#### Key Methods:
```java
// Abstract methods (must be implemented by subclasses)
protected abstract ProtocolSchedule createProtocolSchedule();
protected abstract MiningCoordinator createMiningCoordinator(...);
protected abstract ConsensusContext createConsensusContext(...);
protected abstract PluginServiceFactory createAdditionalPluginServices(...);

// Concrete methods (builder configuration)
public BesuControllerBuilder storageProvider(StorageProvider provider);
public BesuControllerBuilder genesisConfig(GenesisConfig config);
public BesuControllerBuilder networkId(BigInteger networkId);
public BesuControllerBuilder miningConfiguration(MiningConfiguration config);
// ... other configuration methods
```

#### Build Process:
1. **Configuration Phase**: Set various parameters (genesis, storage, network, etc.)
2. **Preparation Phase**: `prepForBuild()` - Subclass-specific preparation work
3. **Creation Phase**: `build()` - Actual component creation
4. **Assembly Phase**: Assemble created components into BesuController

### 3. Consensus Algorithm-Specific Builder Classes

Provides builders tailored to each consensus algorithm's characteristics.

#### 3.1 MainnetBesuControllerBuilder
**Purpose**: For Ethereum Mainnet (PoW - Proof of Work)

**Features**:
- Supports Ethash or Keccak256 PoW algorithms
- Creates PoWMiningCoordinator
- Difficulty adjustment through EpochCalculator
- Uses Mainnet protocol schedule

**Core Code**:
```java
@Override
protected ProtocolSchedule createProtocolSchedule() {
    return MainnetProtocolSchedule.fromConfig(
        genesisConfigOptions,
        Optional.of(isRevertReasonEnabled),
        Optional.of(evmConfiguration),
        miningConfiguration,
        badBlockManager,
        isParallelTxProcessingEnabled,
        balConfiguration,
        metricsSystem);
}

@Override
protected MiningCoordinator createMiningCoordinator(...) {
    final PoWMinerExecutor executor = new PoWMinerExecutor(...);
    return new PoWMiningCoordinator(...);
}
```

#### 3.2 CliqueBesuControllerBuilder
**Purpose**: Clique consensus algorithm (PoA - Proof of Authority)

**Features**:
- Only authorized validators can create blocks
- Epoch-based validator management
- Signature-based block validation
- Primarily used in private networks

**Core Concepts**:
```java
private EpochManager epochManager;              // Epoch management
private BlockInterface blockInterface;          // Clique block interface
private ForksSchedule<CliqueConfigOptions> forksSchedule;

@Override
protected void prepForBuild() {
    localAddress = Util.publicKeyToAddress(nodeKey.getPublicKey());
    epochManager = new EpochManager(blocksPerEpoch);
    forksSchedule = CliqueForksSchedulesFactory.create(genesisConfigOptions);
}
```

#### 3.3 IbftBesuControllerBuilder / QbftBesuControllerBuilder
**Purpose**: IBFT2/QBFT consensus algorithm (Byzantine Fault Tolerant)

**Features**:
- Byzantine fault-tolerant consensus
- Instant finality
- Vote-based consensus among validators
- QBFT is an improved version of IBFT2

**Core Structure**:
- BftContext: Consensus state management
- ValidatorProvider: Validator list management
- BftEventQueue: Consensus event processing
- MessageFactory: Consensus message creation

#### 3.4 MergeBesuControllerBuilder
**Purpose**: PoS (Proof of Stake) after Ethereum Merge

**Features**:
- Consensus mechanism after The Merge
- Handles Execution Layer (Consensus Layer is separate)
- Communicates with Consensus Client via Engine API
- Checkpoint synchronization support

#### 3.5 TransitionBesuControllerBuilder
**Purpose**: Managing transition from PoW to PoS

**Features**:
- Terminal Total Difficulty (TTD) based transition
- Manages both PoW and PoS builders
- Dynamic transition handling

**Transition Logic**:
```java
// In BesuController.Builder
if (configOptions.getTerminalTotalDifficulty().isPresent()) {
    if (syncMode == SyncMode.CHECKPOINT && isCheckpointPoSBlock(configOptions)) {
        return new MergeBesuControllerBuilder().genesisConfig(genesisConfig);
    } else {
        return new TransitionBesuControllerBuilder(
            builder, 
            new MergeBesuControllerBuilder()
        ).genesisConfig(genesisConfig);
    }
}
```

#### 3.6 ConsensusScheduleBesuControllerBuilder
**Purpose**: Scheduling consensus algorithm transitions

**Features**:
- Switches consensus algorithms based on block height
- Primarily used for IBFT to QBFT migration
- Manages multiple builder schedules

**Schedule Structure**:
```java
Map<Long, BesuControllerBuilder> besuControllerBuilderSchedule;
// e.g., {0L: IbftBuilder, 1000000L: QbftBuilder}
```

### 4. PluginServiceFactory

Factory interface that provides additional services for the plugin system.

#### Implementations:
- **NoopPluginServiceFactory**: Default implementation (provides no services)
- **BftQueryPluginServiceFactory**: BFT-related query services
- **CliqueQueryPluginServiceFactory**: Clique-related query services
- **IbftQueryPluginServiceFactory**: IBFT-related query services

**Roles**:
- Provides consensus-specific RPC methods
- Enables plugins to access consensus state

## BesuController Creation Flow

```
1. User Request (CLI or configuration file)
   ↓
2. Create BesuController.Builder
   ↓
3. Call fromEthNetworkConfig() or fromGenesisFile()
   ↓
4. Analyze Genesis configuration → Select appropriate BesuControllerBuilder
   - PoW → MainnetBesuControllerBuilder
   - Clique → CliqueBesuControllerBuilder
   - IBFT2 → IbftBesuControllerBuilder
   - QBFT → QbftBesuControllerBuilder
   - With TTD → TransitionBesuControllerBuilder
   - With migration config → ConsensusScheduleBesuControllerBuilder
   ↓
5. Configure various parameters (Builder pattern)
   - storageProvider()
   - networkId()
   - miningConfiguration()
   - synchronizerConfiguration()
   - Other configurations...
   ↓
6. Call prepForBuild() (per-builder preparation work)
   ↓
7. Call build()
   ↓
8. Create and return BesuController instance
```

## Key Dependencies

The Controller module interacts with the following Besu modules:

1. **ethereum/core**: Blockchain core logic
2. **ethereum/eth**: P2P network and synchronization
3. **ethereum/api**: JSON-RPC API
4. **ethereum/blockcreation**: Block creation and mining
5. **consensus/***: Consensus algorithm implementations
6. **storage**: Data storage
7. **plugin-api**: Plugin system

## Genesis Configuration and Builder Selection

The appropriate builder is automatically selected based on Genesis file configuration:

```json
{
  "config": {
    "chainId": 1337,
    
    // PoW configuration
    "ethash": {},  // → MainnetBesuControllerBuilder
    
    // Or Clique configuration
    "clique": {
      "epochlength": 30000,
      "blockseconds": 15
    },  // → CliqueBesuControllerBuilder
    
    // Or IBFT2 configuration
    "ibft2": {
      "blockperiodseconds": 2,
      "epochlength": 30000
    },  // → IbftBesuControllerBuilder
    
    // Or QBFT configuration
    "qbft": {
      "blockperiodseconds": 2,
      "epochlength": 30000
    },  // → QbftBesuControllerBuilder
    
    // Merge configuration
    "terminalTotalDifficulty": 58750000000000000000000
    // → TransitionBesuControllerBuilder or MergeBesuControllerBuilder
  }
}
```

## Design Principles

1. **Single Responsibility Principle (SRP)**: Each builder handles only one consensus algorithm
2. **Open-Closed Principle (OCP)**: Extensible for new consensus algorithms without modifying existing code
3. **Dependency Inversion Principle (DIP)**: Depend on abstractions, not concrete implementations
4. **Builder Pattern**: Simplifies complex object creation process
5. **Factory Pattern**: Selects appropriate implementation at runtime

## Extension Method

To add a new consensus algorithm:

1. Create a new class extending `BesuControllerBuilder`
2. Implement abstract methods:
   - `createProtocolSchedule()`
   - `createMiningCoordinator()`
   - `createConsensusContext()`
   - `createAdditionalPluginServices()`
3. Override `prepForBuild()` method (if needed)
4. Add new consensus type branch in `BesuController.Builder.fromGenesisFile()`

## References

- [Besu Official Documentation](https://besu.hyperledger.org)
- [Consensus Protocols Comparison](https://besu.hyperledger.org/public-networks/concepts/consensus-protocols)
- [Protocol Schedule](../ethereum/core/README.md)
- [Plugin API](../plugin-api/README.md)

## Summary

The `app/controller` module handles the core initialization and management logic of Besu nodes:

- **BesuController**: Central controller managing all major node components
- **BesuControllerBuilder**: Simplifies complex initialization process with Builder pattern
- **Consensus-specific builders**: Provides configuration tailored to each consensus algorithm
- **Flexible design**: Easy to add and extend new consensus algorithms

This structure allows Besu to flexibly support various consensus algorithms and network configurations.
