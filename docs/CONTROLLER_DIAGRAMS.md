# Besu Controller 모듈 클래스 다이어그램 및 관계도

## 1. 클래스 계층 구조

```
BesuController (Core Class)
    ↑ creates
    │
BesuControllerBuilder (Abstract)
    │
    ├── MainnetBesuControllerBuilder
    │   └── Creates: PoWMiningCoordinator, MainnetProtocolSchedule
    │
    ├── CliqueBesuControllerBuilder
    │   └── Creates: CliqueMiningCoordinator, CliqueProtocolSchedule
    │
    ├── IbftBesuControllerBuilder
    │   └── Creates: BftMiningCoordinator, IbftProtocolSchedule
    │
    ├── IbftLegacyBesuControllerBuilder
    │   └── Creates: Legacy IBFT components
    │
    ├── QbftBesuControllerBuilder
    │   └── Creates: BftMiningCoordinator, QbftProtocolSchedule
    │
    ├── MergeBesuControllerBuilder
    │   └── Creates: PoS components, Merge-specific services
    │
    ├── TransitionBesuControllerBuilder
    │   ├── Wraps: Pre-merge builder (PoW/Clique/BFT)
    │   └── Wraps: Post-merge builder (MergeBesuControllerBuilder)
    │
    └── ConsensusScheduleBesuControllerBuilder
        └── Manages: Map<BlockNumber, BesuControllerBuilder>
```

## 2. BesuController 내부 컴포넌트 관계도

```
┌─────────────────────────────────────────────────────────────────┐
│                        BesuController                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌────────────────────┐      ┌─────────────────────┐            │
│  │ ProtocolSchedule   │◄─────│ GenesisConfigOptions│            │
│  │                    │      └─────────────────────┘            │
│  │ - Chain rules      │                                          │
│  │ - EVM config       │      ┌─────────────────────┐            │
│  │ - Fork transitions │      │  ProtocolContext    │            │
│  └────────────────────┘      │                     │            │
│           │                  │ - Blockchain        │            │
│           │                  │ - WorldState        │            │
│           │                  │ - ConsensusContext  │            │
│           ▼                  └─────────────────────┘            │
│  ┌────────────────────┐               │                         │
│  │ EthProtocolManager │◄──────────────┘                         │
│  │                    │                                          │
│  │ - P2P networking   │      ┌─────────────────────┐            │
│  │ - Peer management  │      │   Synchronizer      │            │
│  │ - Message handling │◄─────│                     │            │
│  └────────────────────┘      │ - Block sync        │            │
│           │                  │ - State sync        │            │
│           │                  │ - Fast/Snap/Full    │            │
│           │                  └─────────────────────┘            │
│           ▼                           │                          │
│  ┌────────────────────┐              │                          │
│  │  TransactionPool   │◄─────────────┘                          │
│  │                    │                                          │
│  │ - Pending txs      │      ┌─────────────────────┐            │
│  │ - Validation       │      │ MiningCoordinator   │            │
│  │ - Prioritization   │─────►│                     │            │
│  └────────────────────┘      │ - Block creation    │            │
│                               │ - Mining execution  │            │
│                               └─────────────────────┘            │
│                                        │                          │
│  ┌────────────────────┐               │                          │
│  │  StorageProvider   │               │                          │
│  │                    │               ▼                          │
│  │ - Blockchain DB    │      ┌─────────────────────┐            │
│  │ - World State DB   │      │ JsonRpcMethods      │            │
│  │ - Key-value store  │      │                     │            │
│  └────────────────────┘      │ - eth_*             │            │
│                               │ - admin_*           │            │
│                               │ - consensus-specific│            │
│                               └─────────────────────┘            │
└───────────────────────────────────────────────────────────────────┘
```

## 3. Builder 패턴 흐름도

```
┌──────────────────────────────────────────────────────────────────┐
│                     BesuController.Builder                        │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│          fromGenesisFile(GenesisConfig, SyncMode)                 │
│                                                                    │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │ if (isConsensusMigration)                                    │ │
│  │   → ConsensusScheduleBesuControllerBuilder                   │ │
│  │                                                               │ │
│  │ else if (isPowAlgorithm)                                     │ │
│  │   → MainnetBesuControllerBuilder                            │ │
│  │                                                               │ │
│  │ else if (isIbft2)                                            │ │
│  │   → IbftBesuControllerBuilder                               │ │
│  │                                                               │ │
│  │ else if (isQbft)                                             │ │
│  │   → QbftBesuControllerBuilder                               │ │
│  │                                                               │ │
│  │ else if (isClique)                                           │ │
│  │   → CliqueBesuControllerBuilder                             │ │
│  │                                                               │ │
│  │ if (hasTerminalTotalDifficulty)                              │ │
│  │   Wrap with → TransitionBesuControllerBuilder                │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│              Selected BesuControllerBuilder                       │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                     Configuration Phase                           │
│  builder.storageProvider(...)                                     │
│         .networkId(...)                                           │
│         .miningConfiguration(...)                                 │
│         .synchronizerConfiguration(...)                           │
│         .nodeKey(...)                                             │
│         .metricsSystem(...)                                       │
│         ... (more configurations)                                 │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                       prepForBuild()                              │
│  - Consensus-specific preparation                                 │
│  - Validator setup                                                │
│  - Protocol configuration                                         │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                          build()                                  │
│                                                                    │
│  1. createProtocolSchedule()                                      │
│  2. createProtocolContext()                                       │
│  3. createEthProtocolManager()                                    │
│  4. createSynchronizer()                                          │
│  5. createTransactionPool()                                       │
│  6. createMiningCoordinator()                                     │
│  7. createAdditionalServices()                                    │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                     ┌────────────────┐
                     │ BesuController │
                     └────────────────┘
```

## 4. 합의 알고리즘별 컴포넌트 매핑

```
┌──────────────┬─────────────────────┬──────────────────────┬────────────────────┐
│   Consensus  │  MiningCoordinator  │   ProtocolSchedule   │  ConsensusContext  │
├──────────────┼─────────────────────┼──────────────────────┼────────────────────┤
│              │                     │                      │                    │
│   Mainnet    │ PoWMiningCoordinator│ MainnetProtocol      │       null         │
│   (PoW)      │  - PoWMinerExecutor │   Schedule           │                    │
│              │  - Ethash/Keccak256 │  - Frontier to       │                    │
│              │                     │    Shanghai          │                    │
├──────────────┼─────────────────────┼──────────────────────┼────────────────────┤
│              │                     │                      │                    │
│   Clique     │CliqueMiningCoord.   │ CliqueProtocol       │  CliqueContext     │
│   (PoA)      │  - CliqueMiner      │   Schedule           │  - ValidatorProvider│
│              │    Executor         │  - Clique rules      │  - EpochManager    │
│              │  - Signer votes     │                      │  - Snapshots       │
├──────────────┼─────────────────────┼──────────────────────┼────────────────────┤
│              │                     │                      │                    │
│   IBFT2      │ BftMiningCoordinator│ IbftProtocol         │   BftContext       │
│   (BFT)      │  - Proposer         │   Schedule           │  - ValidatorProvider│
│              │    selection        │  - IBFT rules        │  - BftEventQueue   │
│              │  - Round-based      │                      │  - VoteTracker     │
├──────────────┼─────────────────────┼──────────────────────┼────────────────────┤
│              │                     │                      │                    │
│   QBFT       │ BftMiningCoordinator│ QbftProtocol         │   BftContext       │
│   (BFT)      │  - Enhanced IBFT    │   Schedule           │  - ValidatorProvider│
│              │  - Contract-based   │  - QBFT rules        │  - QbftEventQueue  │
│              │    validators       │                      │  - Message tracking│
├──────────────┼─────────────────────┼──────────────────────┼────────────────────┤
│              │                     │                      │                    │
│   Merge      │MergeMiningCoord.    │ MergeProtocol        │   MergeContext     │
│   (PoS)      │  - Engine API       │   Schedule           │  - ForkchoiceState │
│              │  - External beacon  │  - PoS rules         │  - PayloadBuilding │
│              │                     │  - Bellatrix+        │                    │
└──────────────┴─────────────────────┴──────────────────────┴────────────────────┘
```

## 5. 플러그인 서비스 팩토리 관계도

```
┌────────────────────────────────────────────────────────────┐
│              PluginServiceFactory (Interface)              │
│  + create(): Map<String, PluginService>                    │
└────────────────────────────────────────────────────────────┘
                         △
                         │ implements
         ┌───────────────┼────────────────┬────────────────┐
         │               │                │                │
┌────────┴─────┐  ┌──────┴──────┐  ┌──────┴──────┐  ┌──────┴──────┐
│NoopPlugin    │  │BftQuery     │  │CliqueQuery  │  │IbftQuery    │
│ServiceFactory│  │PluginService│  │PluginService│  │PluginService│
│              │  │Factory      │  │Factory      │  │Factory      │
│- Returns {}  │  │             │  │             │  │             │
└──────────────┘  └─────────────┘  └─────────────┘  └─────────────┘
                         │                │                │
                         ▼                ▼                ▼
                  Provides:        Provides:        Provides:
                  - BftQuery       - CliqueQuery    - IbftQuery
                  - Validator      - Validator      - Validator
                    info             info             info
                  - Proposal       - Signer list    - Round info
                    tracking       - Snapshot       - Proposer
                                     queries          tracking
```

## 6. TransitionBesuControllerBuilder 동작 방식

```
┌─────────────────────────────────────────────────────────────────┐
│           TransitionBesuControllerBuilder                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────────────┐   ┌──────────────────────────┐   │
│  │  Pre-Merge Builder       │   │  Post-Merge Builder      │   │
│  │  (PoW/Clique/BFT)        │   │  (MergeBuilder)          │   │
│  │                          │   │                          │   │
│  │ - Regular consensus      │   │ - Engine API             │   │
│  │ - Block production       │   │ - Beacon interaction     │   │
│  └──────────────────────────┘   └──────────────────────────┘   │
│              │                              │                    │
│              └──────────────┬───────────────┘                    │
│                             │                                    │
│                             ▼                                    │
│              ┌──────────────────────────┐                        │
│              │ Dynamic Switch Logic     │                        │
│              │                          │                        │
│              │ if (totalDifficulty >=   │                        │
│              │     terminalTotalDiff)   │                        │
│              │   USE: Post-Merge        │                        │
│              │ else                     │                        │
│              │   USE: Pre-Merge         │                        │
│              └──────────────────────────┘                        │
└───────────────────────────────────────────────────────────────────┘

Timeline:
─────────────────────────────────────────────────────────────────────►
  Pre-Merge Era       │        The Merge (TTD)      │   Post-Merge Era
                      │                             │
  PoW/Clique/BFT  ────┼────────────────────────────►│   PoS (Beacon)
  blocks              │                             │   blocks
```

## 7. 전체 시스템 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Besu Application                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                     BesuController                             │  │
│  │                   (Central Orchestrator)                       │  │
│  └───────────────────────────────────────────────────────────────┘  │
│           │                    │                    │                │
│           ▼                    ▼                    ▼                │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐        │
│  │   Protocol     │  │   Blockchain   │  │   Network      │        │
│  │   Execution    │  │   State        │  │   Layer        │        │
│  │                │  │                │  │                │        │
│  │ - EVM          │  │ - Blocks       │  │ - P2P          │        │
│  │ - Tx process   │  │ - Receipts     │  │ - Discovery    │        │
│  │ - Gas calc     │  │ - World state  │  │ - Sync         │        │
│  └────────────────┘  └────────────────┘  └────────────────┘        │
│           │                    │                    │                │
│           └────────────────────┴────────────────────┘                │
│                                │                                     │
│                                ▼                                     │
│           ┌────────────────────────────────────┐                     │
│           │         Consensus Layer            │                     │
│           │                                    │                     │
│           │  PoW  │ Clique │ IBFT2 │ QBFT │ PoS│                     │
│           └────────────────────────────────────┘                     │
│                                │                                     │
│                                ▼                                     │
│           ┌────────────────────────────────────┐                     │
│           │         Storage Layer              │                     │
│           │                                    │                     │
│           │  RocksDB  │  LevelDB  │  Memory    │                     │
│           └────────────────────────────────────┘                     │
│                                                                       │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                     API Layer (JSON-RPC)                       │  │
│  │  eth_* │ admin_* │ debug_* │ net_* │ consensus-specific        │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                     Plugin System                              │  │
│  │  Custom RPC │ Event Listeners │ Permissioning │ Metrics        │  │
│  └───────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────┘
```

## 8. 초기화 시퀀스 다이어그램

```
User/CLI              Builder            ControllerBuilder      BesuController
   │                    │                      │                      │
   │─ start() ─────────►│                      │                      │
   │                    │                      │                      │
   │                    │─ fromGenesisFile()──►│                      │
   │                    │                      │                      │
   │                    │                      │─ Select correct      │
   │                    │                      │  builder type        │
   │                    │                      │                      │
   │                    │◄─ return builder ───│                      │
   │                    │                      │                      │
   │                    │─ configure() ───────►│                      │
   │                    │  storageProvider     │                      │
   │                    │  networkId           │                      │
   │                    │  miningConfig        │                      │
   │                    │  ...                 │                      │
   │                    │                      │                      │
   │                    │─ build() ───────────►│                      │
   │                    │                      │                      │
   │                    │                      │─ prepForBuild()      │
   │                    │                      │                      │
   │                    │                      │─ createProtocol      │
   │                    │                      │  Schedule()          │
   │                    │                      │                      │
   │                    │                      │─ createProtocol      │
   │                    │                      │  Context()           │
   │                    │                      │                      │
   │                    │                      │─ createMining        │
   │                    │                      │  Coordinator()       │
   │                    │                      │                      │
   │                    │                      │─ createSynchronizer()│
   │                    │                      │                      │
   │                    │                      │─ new BesuController()│
   │                    │                      │                      │
   │                    │                      │──────────────────────►│
   │                    │                      │                      │
   │                    │◄─────────────────────┼──── instance ────────│
   │                    │                      │                      │
   │◄─ controller ──────│                      │                      │
   │                    │                      │                      │
   │─ startNode() ──────┼──────────────────────┼──────────────────────►│
   │                    │                      │                      │
```

이 다이어그램들은 Besu Controller 모듈의 구조와 동작 방식을 시각적으로 이해하는 데 도움이 됩니다.
