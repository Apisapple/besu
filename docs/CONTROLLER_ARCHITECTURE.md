# Besu App Controller 모듈 아키텍처 가이드

## 개요

이 문서는 Hyperledger Besu 프로젝트의 `app/controller` 모듈에 대한 상세한 설명을 제공합니다. Controller 모듈은 Besu 노드의 핵심 구성 요소들을 초기화하고 관리하는 중요한 역할을 담당합니다.

## 디렉토리 구조

```
app/src/main/java/org/hyperledger/besu/controller/
├── BesuController.java                          # 메인 컨트롤러 클래스
├── BesuControllerBuilder.java                   # 추상 빌더 클래스 (1390 lines)
├── MainnetBesuControllerBuilder.java            # Mainnet용 빌더
├── CliqueBesuControllerBuilder.java             # Clique 합의 알고리즘용 빌더
├── IbftBesuControllerBuilder.java               # IBFT2 합의 알고리즘용 빌더
├── QbftBesuControllerBuilder.java               # QBFT 합의 알고리즘용 빌더
├── IbftLegacyBesuControllerBuilder.java         # IBFT Legacy용 빌더
├── MergeBesuControllerBuilder.java              # PoS Merge용 빌더
├── TransitionBesuControllerBuilder.java         # PoW에서 PoS 전환용 빌더
├── ConsensusScheduleBesuControllerBuilder.java  # 합의 알고리즘 전환 스케줄용 빌더
├── PluginServiceFactory.java                    # 플러그인 서비스 팩토리 인터페이스
├── NoopPluginServiceFactory.java                # 기본 플러그인 서비스 팩토리
├── BftQueryPluginServiceFactory.java            # BFT 쿼리 플러그인 서비스
├── CliqueQueryPluginServiceFactory.java         # Clique 쿼리 플러그인 서비스
├── IbftQueryPluginServiceFactory.java           # IBFT 쿼리 플러그인 서비스
└── MiningConfigurationOverrides.java            # 마이닝 설정 오버라이드 인터페이스
```

## 주요 컴포넌트

### 1. BesuController

`BesuController`는 Besu 노드의 핵심 컴포넌트를 관리하는 메인 컨트롤러입니다.

#### 주요 책임:
- 프로토콜 스케줄 관리 (ProtocolSchedule)
- 프로토콜 컨텍스트 관리 (ProtocolContext)
- 이더리움 프로토콜 매니저 (EthProtocolManager)
- 동기화 관리자 (Synchronizer)
- 트랜잭션 풀 (TransactionPool)
- 마이닝 코디네이터 (MiningCoordinator)
- JSON-RPC 메서드 제공
- 리소스 정리 (Closeable 구현)

#### 주요 필드:
```java
private final ProtocolSchedule protocolSchedule;         // 프로토콜 버전별 실행 규칙
private final ProtocolContext protocolContext;           // 블록체인 상태 및 컨텍스트
private final EthProtocolManager ethProtocolManager;     // P2P 네트워크 관리
private final Synchronizer synchronizer;                 // 블록 동기화 관리
private final TransactionPool transactionPool;           // 트랜잭션 메모리 풀
private final MiningCoordinator miningCoordinator;       // 블록 생성 조정
private final StorageProvider storageProvider;           // 데이터 저장소 제공
private final TransactionSimulator transactionSimulator; // 트랜잭션 시뮬레이션
```

#### 생명주기:
1. Builder 패턴을 통한 생성
2. 각종 서비스 초기화
3. 노드 실행 중 컴포넌트 관리
4. `close()` 메서드를 통한 리소스 정리

### 2. BesuControllerBuilder (추상 클래스)

모든 구체적인 빌더 클래스의 기반이 되는 추상 클래스로, Builder 패턴을 구현합니다.

#### 설계 패턴:
- **Builder Pattern**: 복잡한 객체 생성을 단계별로 처리
- **Template Method Pattern**: 공통 로직은 추상 클래스에, 특화 로직은 하위 클래스에
- **Factory Pattern**: 합의 알고리즘에 따라 적절한 빌더 생성

#### 주요 메서드:
```java
// 추상 메서드 (하위 클래스에서 구현 필요)
protected abstract ProtocolSchedule createProtocolSchedule();
protected abstract MiningCoordinator createMiningCoordinator(...);
protected abstract ConsensusContext createConsensusContext(...);
protected abstract PluginServiceFactory createAdditionalPluginServices(...);

// 구체 메서드 (빌더 설정)
public BesuControllerBuilder storageProvider(StorageProvider provider);
public BesuControllerBuilder genesisConfig(GenesisConfig config);
public BesuControllerBuilder networkId(BigInteger networkId);
public BesuControllerBuilder miningConfiguration(MiningConfiguration config);
// ... 기타 설정 메서드들
```

#### 빌드 프로세스:
1. **설정 단계**: 각종 파라미터 설정 (genesis, storage, network 등)
2. **준비 단계**: `prepForBuild()` - 하위 클래스별 준비 작업
3. **생성 단계**: `build()` - 실제 컴포넌트 생성
4. **조립 단계**: 생성된 컴포넌트들을 BesuController로 조립

### 3. 합의 알고리즘별 빌더 클래스

각 합의 알고리즘의 특성에 맞는 빌더를 제공합니다.

#### 3.1 MainnetBesuControllerBuilder
**용도**: 이더리움 메인넷용 (PoW - Proof of Work)

**특징**:
- Ethash 또는 Keccak256 PoW 알고리즘 지원
- PoWMiningCoordinator 생성
- EpochCalculator를 통한 난이도 조정
- Mainnet 프로토콜 스케줄 사용

**핵심 코드**:
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
**용도**: Clique 합의 알고리즘 (PoA - Proof of Authority)

**특징**:
- 권한 있는 검증자(Validator)만 블록 생성 가능
- 에폭(epoch) 기반 검증자 관리
- 서명 기반 블록 검증
- 주로 프라이빗 네트워크에서 사용

**핵심 개념**:
```java
private EpochManager epochManager;              // 에폭 관리
private BlockInterface blockInterface;          // Clique 블록 인터페이스
private ForksSchedule<CliqueConfigOptions> forksSchedule;

@Override
protected void prepForBuild() {
    localAddress = Util.publicKeyToAddress(nodeKey.getPublicKey());
    epochManager = new EpochManager(blocksPerEpoch);
    forksSchedule = CliqueForksSchedulesFactory.create(genesisConfigOptions);
}
```

#### 3.3 IbftBesuControllerBuilder / QbftBesuControllerBuilder
**용도**: IBFT2/QBFT 합의 알고리즘 (Byzantine Fault Tolerant)

**특징**:
- 비잔틴 장애 허용 합의
- 즉각적인 최종성(Instant Finality)
- 검증자 간 투표 기반 합의
- QBFT는 IBFT2의 개선 버전

**핵심 구조**:
- BftContext: 합의 상태 관리
- ValidatorProvider: 검증자 목록 관리
- BftEventQueue: 합의 이벤트 처리
- MessageFactory: 합의 메시지 생성

#### 3.4 MergeBesuControllerBuilder
**용도**: Ethereum Merge 이후 PoS (Proof of Stake)

**특징**:
- The Merge 이후 합의 메커니즘
- Execution Layer 담당 (Consensus Layer는 별도)
- Engine API를 통한 Consensus Client와 통신
- 체크포인트 동기화 지원

#### 3.5 TransitionBesuControllerBuilder
**용도**: PoW에서 PoS로의 전환 관리

**특징**:
- Terminal Total Difficulty (TTD) 기반 전환
- PoW와 PoS 빌더를 모두 관리
- 동적 전환 처리

**전환 로직**:
```java
// BesuController.Builder에서
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
**용도**: 합의 알고리즘 간 전환 스케줄링

**특징**:
- 블록 높이에 따라 합의 알고리즘 전환
- 주로 IBFT에서 QBFT로 마이그레이션에 사용
- 다중 빌더 스케줄 관리

**스케줄 구조**:
```java
Map<Long, BesuControllerBuilder> besuControllerBuilderSchedule;
// 예: {0L: IbftBuilder, 1000000L: QbftBuilder}
```

### 4. PluginServiceFactory

플러그인 시스템을 위한 추가 서비스를 제공하는 팩토리 인터페이스입니다.

#### 구현체:
- **NoopPluginServiceFactory**: 기본 구현 (아무 서비스도 제공하지 않음)
- **BftQueryPluginServiceFactory**: BFT 관련 쿼리 서비스
- **CliqueQueryPluginServiceFactory**: Clique 관련 쿼리 서비스
- **IbftQueryPluginServiceFactory**: IBFT 관련 쿼리 서비스

**역할**:
- 합의 알고리즘별 특화된 RPC 메서드 제공
- 플러그인이 합의 상태에 접근할 수 있도록 지원

## BesuController 생성 흐름

```
1. 사용자 요청 (CLI 또는 설정 파일)
   ↓
2. BesuController.Builder 생성
   ↓
3. fromEthNetworkConfig() 또는 fromGenesisFile() 호출
   ↓
4. Genesis 설정 분석 → 적절한 BesuControllerBuilder 선택
   - PoW → MainnetBesuControllerBuilder
   - Clique → CliqueBesuControllerBuilder
   - IBFT2 → IbftBesuControllerBuilder
   - QBFT → QbftBesuControllerBuilder
   - TTD 설정 시 → TransitionBesuControllerBuilder
   - 마이그레이션 설정 시 → ConsensusScheduleBesuControllerBuilder
   ↓
5. 각종 파라미터 설정 (Builder 패턴)
   - storageProvider()
   - networkId()
   - miningConfiguration()
   - synchronizerConfiguration()
   - 기타 설정들...
   ↓
6. prepForBuild() 호출 (각 빌더별 준비 작업)
   ↓
7. build() 호출
   ↓
8. BesuController 인스턴스 생성 및 반환
```

## 주요 의존성

Controller 모듈은 다음 Besu 모듈들과 상호작용합니다:

1. **ethereum/core**: 블록체인 핵심 로직
2. **ethereum/eth**: P2P 네트워크 및 동기화
3. **ethereum/api**: JSON-RPC API
4. **ethereum/blockcreation**: 블록 생성 및 마이닝
5. **consensus/***: 각 합의 알고리즘 구현
6. **storage**: 데이터 저장소
7. **plugin-api**: 플러그인 시스템

## Genesis 설정과 빌더 선택

Genesis 파일의 설정에 따라 자동으로 적절한 빌더가 선택됩니다:

```json
{
  "config": {
    "chainId": 1337,
    
    // PoW 설정
    "ethash": {},  // → MainnetBesuControllerBuilder
    
    // 또는 Clique 설정
    "clique": {
      "epochlength": 30000,
      "blockseconds": 15
    },  // → CliqueBesuControllerBuilder
    
    // 또는 IBFT2 설정
    "ibft2": {
      "blockperiodseconds": 2,
      "epochlength": 30000
    },  // → IbftBesuControllerBuilder
    
    // 또는 QBFT 설정
    "qbft": {
      "blockperiodseconds": 2,
      "epochlength": 30000
    },  // → QbftBesuControllerBuilder
    
    // Merge 설정
    "terminalTotalDifficulty": 58750000000000000000000
    // → TransitionBesuControllerBuilder 또는 MergeBesuControllerBuilder
  }
}
```

## 설계 원칙

1. **단일 책임 원칙 (SRP)**: 각 빌더는 하나의 합의 알고리즘만 담당
2. **개방-폐쇄 원칙 (OCP)**: 새로운 합의 알고리즘 추가 시 기존 코드 수정 없이 확장
3. **의존성 역전 원칙 (DIP)**: 추상화에 의존, 구체 구현에 의존하지 않음
4. **빌더 패턴**: 복잡한 객체 생성 과정을 단순화
5. **팩토리 패턴**: 런타임에 적절한 구현체 선택

## 확장 방법

새로운 합의 알고리즘을 추가하려면:

1. `BesuControllerBuilder`를 상속받는 새 클래스 생성
2. 추상 메서드 구현:
   - `createProtocolSchedule()`
   - `createMiningCoordinator()`
   - `createConsensusContext()`
   - `createAdditionalPluginServices()`
3. `prepForBuild()` 메서드 오버라이드 (필요시)
4. `BesuController.Builder.fromGenesisFile()`에 새 합의 타입 분기 추가

## 참고 자료

- [Besu 공식 문서](https://besu.hyperledger.org)
- [합의 알고리즘 비교](https://besu.hyperledger.org/public-networks/concepts/consensus-protocols)
- [프로토콜 스케줄](../ethereum/core/README.md)
- [플러그인 API](../plugin-api/README.md)

## 요약

`app/controller` 모듈은 Besu 노드의 핵심 초기화 및 관리 로직을 담당합니다:

- **BesuController**: 노드의 모든 주요 컴포넌트를 관리하는 중앙 컨트롤러
- **BesuControllerBuilder**: Builder 패턴으로 복잡한 초기화 과정 단순화
- **합의별 빌더들**: 각 합의 알고리즘의 특성에 맞는 구성 제공
- **유연한 설계**: 새로운 합의 알고리즘 추가 및 확장 용이

이 구조를 통해 Besu는 다양한 합의 알고리즘과 네트워크 설정을 유연하게 지원할 수 있습니다.
