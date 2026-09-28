// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title SethGEscrow
 * @notice Trustless escrow and audit settlement for informal scrap and e-waste trades.
 *         Protects both kabadiwalas (vendors) and certified recyclers.
 */
contract SethGEscrow {
    enum EscrowStatus { NONE, HELD, RELEASED, DISPUTED, REFUNDED }

    struct LotEscrow {
        string lotId;
        address payable collector;
        address payable recycler;
        uint256 amount;
        EscrowStatus status;
        bytes32 handoverProofHash;
        uint256 heldAt;
        uint256 settledAt;
    }

    address public owner;
    mapping(string => LotEscrow) public escrows; // lotId => LotEscrow

    event EscrowHeld(string indexed lotId, address indexed recycler, address indexed collector, uint256 amount);
    event EscrowReleased(string indexed lotId, address indexed collector, uint256 amount, bytes32 proofHash);
    event EscrowDisputed(string indexed lotId, address indexed raisedBy, string reason);
    event EscrowResolved(string indexed lotId, address indexed payoutRecipient, uint256 payoutAmount);

    modifier onlyOwner() {
        require(msg.sender == owner, "Only platform owner or relayer can call");
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    /**
     * @notice Locks payment in escrow when an offer is accepted.
     */
    function lockEscrow(
        string calldata lotId,
        address payable collector
    ) external payable {
        require(msg.value > 0, "Escrow amount must be > 0");
        require(escrows[lotId].status == EscrowStatus.NONE, "Escrow already exists for lot");

        escrows[lotId] = LotEscrow({
            lotId: lotId,
            collector: collector,
            recycler: payable(msg.sender),
            amount: msg.value,
            status: EscrowStatus.HELD,
            handoverProofHash: bytes32(0),
            heldAt: block.timestamp,
            settledAt: 0
        });

        emit EscrowHeld(lotId, msg.sender, collector, msg.value);
    }

    /**
     * @notice Releases escrow to collector upon successful two-sided verified handover (OTP + scale weight).
     */
    function releaseEscrow(
        string calldata lotId,
        bytes32 proofHash
    ) external onlyOwner {
        LotEscrow storage item = escrows[lotId];
        require(item.status == EscrowStatus.HELD, "Escrow is not in HELD state");
        require(proofHash != bytes32(0), "Valid proof hash required");

        item.status = EscrowStatus.RELEASED;
        item.handoverProofHash = proofHash;
        item.settledAt = block.timestamp;

        (bool sent, ) = item.collector.call{value: item.amount}("");
        require(sent, "Failed to release escrow funds");

        emit EscrowReleased(lotId, item.collector, item.amount, proofHash);
    }

    /**
     * @notice Freezes escrow funds when a weight or quality dispute is raised.
     */
    function freezeForDispute(
        string calldata lotId,
        string calldata reason
    ) external onlyOwner {
        LotEscrow storage item = escrows[lotId];
        require(item.status == EscrowStatus.HELD, "Only HELD escrow can be disputed");

        item.status = EscrowStatus.DISPUTED;
        emit EscrowDisputed(lotId, msg.sender, reason);
    }

    /**
     * @notice Resolves dispute with finalized payout split or refund.
     */
    function resolveDispute(
        string calldata lotId,
        address payable payoutRecipient,
        uint256 payoutAmount
    ) external onlyOwner {
        LotEscrow storage item = escrows[lotId];
        require(item.status == EscrowStatus.DISPUTED, "Escrow is not in DISPUTED state");
        require(payoutAmount <= item.amount, "Payout exceeds held amount");

        item.status = EscrowStatus.RELEASED;
        item.settledAt = block.timestamp;

        uint256 refundAmount = item.amount - payoutAmount;
        if (payoutAmount > 0) {
            (bool sentPayout, ) = payoutRecipient.call{value: payoutAmount}("");
            require(sentPayout, "Payout failed");
        }
        if (refundAmount > 0) {
            (bool sentRefund, ) = item.recycler.call{value: refundAmount}("");
            require(sentRefund, "Refund failed");
        }

        emit EscrowResolved(lotId, payoutRecipient, payoutAmount);
    }

    /**
     * @notice Returns escrow details for public verification.
     */
    function getEscrow(string calldata lotId) external view returns (
        address collector,
        address recycler,
        uint256 amount,
        EscrowStatus status,
        bytes32 handoverProofHash,
        uint256 heldAt,
        uint256 settledAt
    ) {
        LotEscrow memory item = escrows[lotId];
        return (
            item.collector,
            item.recycler,
            item.amount,
            item.status,
            item.handoverProofHash,
            item.heldAt,
            item.settledAt
        );
    }
}
