// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title EPRCertificate
 * @notice Verifiable, tamper-proof Extended Producer Responsibility (EPR) Recycling Certificate.
 *         Mints proof of authenticated e-waste diversion from landfill into certified recycling streams.
 */
contract EPRCertificate {
    struct CertificateData {
        uint256 tokenId;
        string lotId;
        string handoverId;
        string materialCategory;
        uint256 actualWeightGrams;   // stored in grams for precision (e.g. 25.5 kg = 25500 g)
        bytes32 collectorIdentityHash;
        address recyclerAddress;
        bytes32 scaleProofHash;       // hash of photos + scale readouts
        uint256 issuedAt;
        bool isRedeemed;             // prevents double-counting for CPCB compliance
        string ipfsMetadataUri;
    }

    string public name = "Seth G Verifiable EPR Certificate";
    string public symbol = "SETHG-EPR";
    address public owner;
    uint256 private _nextTokenId;

    mapping(uint256 => CertificateData) private _certificates;
    mapping(string => uint256) private _lotToTokenId; // lotId => tokenId

    event CertificateMinted(
        uint256 indexed tokenId,
        string indexed lotId,
        address indexed recycler,
        string materialCategory,
        uint256 actualWeightGrams,
        bytes32 scaleProofHash
    );

    event CertificateRedeemed(uint256 indexed tokenId, address indexed auditor);

    modifier onlyOwner() {
        require(msg.sender == owner, "Only authorized minter");
        _;
    }

    constructor() {
        owner = msg.sender;
        _nextTokenId = 1;
    }

    /**
     * @notice Mints an EPR recycling compliance certificate upon verified lot handover.
     */
    function mintCertificate(
        string calldata lotId,
        string calldata handoverId,
        string calldata materialCategory,
        uint256 actualWeightGrams,
        bytes32 collectorIdentityHash,
        address recyclerAddress,
        bytes32 scaleProofHash,
        string calldata ipfsMetadataUri
    ) external onlyOwner returns (uint256) {
        require(_lotToTokenId[lotId] == 0, "Certificate already minted for this lot");
        require(actualWeightGrams > 0, "Weight must be > 0");

        uint256 tokenId = _nextTokenId++;

        _certificates[tokenId] = CertificateData({
            tokenId: tokenId,
            lotId: lotId,
            handoverId: handoverId,
            materialCategory: materialCategory,
            actualWeightGrams: actualWeightGrams,
            collectorIdentityHash: collectorIdentityHash,
            recyclerAddress: recyclerAddress,
            scaleProofHash: scaleProofHash,
            issuedAt: block.timestamp,
            isRedeemed: false,
            ipfsMetadataUri: ipfsMetadataUri
        });

        _lotToTokenId[lotId] = tokenId;

        emit CertificateMinted(
            tokenId,
            lotId,
            recyclerAddress,
            materialCategory,
            actualWeightGrams,
            scaleProofHash
        );

        return tokenId;
    }

    /**
     * @notice Marks certificate as redeemed for EPR statutory quota, preventing double-counting.
     */
    function redeemCertificate(uint256 tokenId) external onlyOwner {
        CertificateData storage cert = _certificates[tokenId];
        require(cert.tokenId != 0, "Certificate does not exist");
        require(!cert.isRedeemed, "Certificate already redeemed");

        cert.isRedeemed = true;
        emit CertificateRedeemed(tokenId, msg.sender);
    }

    /**
     * @notice Public verification method for auditors, CPCB inspectors, or mobile clients.
     */
    function getCertificate(uint256 tokenId) external view returns (CertificateData memory) {
        require(_certificates[tokenId].tokenId != 0, "Certificate not found");
        return _certificates[tokenId];
    }

    /**
     * @notice Lookup certificate token ID by lot ID.
     */
    function getCertificateByLotId(string calldata lotId) external view returns (CertificateData memory) {
        uint256 tokenId = _lotToTokenId[lotId];
        require(tokenId != 0, "No certificate for this lot");
        return _certificates[tokenId];
    }
}
