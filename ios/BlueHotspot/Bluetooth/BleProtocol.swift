import CoreBluetooth
import Foundation

struct BleUuids {
    nonisolated(unsafe) static let service = CBUUID(string: "8B5F0001-7D3E-4E4A-9F1C-4B20F4E9A001")
    nonisolated(unsafe) static let deviceInfo = CBUUID(string: "8B5F0002-7D3E-4E4A-9F1C-4B20F4E9A001")
    nonisolated(unsafe) static let command = CBUUID(string: "8B5F0003-7D3E-4E4A-9F1C-4B20F4E9A001")
    nonisolated(unsafe) static let event = CBUUID(string: "8B5F0004-7D3E-4E4A-9F1C-4B20F4E9A001")
    nonisolated(unsafe) static let pairing = CBUUID(string: "8B5F0005-7D3E-4E4A-9F1C-4B20F4E9A001")
}

enum BleMessage: UInt8 {
    case hello = 1
    case helloAck = 2
    case getStatus = 3
    case status = 4
    case startHotspot = 5
    case hotspotStarting = 6
    case hotspotReady = 7
    case hotspotFailed = 8
    case stopHotspot = 9
    case hotspotStopped = 10
    case ping = 11
    case pong = 12
    case error = 13
}

struct BleFrame {
    let type: BleMessage
    let requestId: UUID
    let payload: String
}

enum BleProtocolError: Error {
    case invalidFrame
    case invalidLength
    case unsupportedVersion
    case invalidUtf8
    case unknownMessage
}

enum BleFrameCodec {
    static func encode(_ frame: BleFrame) throws -> Data {
        guard let payload = frame.payload.data(using: .utf8), payload.count <= 512 else {
            throw BleProtocolError.invalidLength
        }
        var body = Data([1, frame.type.rawValue])
        body.append(contentsOf: withUnsafeBytes(of: frame.requestId.uuid) { Array($0) })
        body.append(payload)
        guard body.count <= 530 else { throw BleProtocolError.invalidLength }
        var result = Data([UInt8(body.count >> 8), UInt8(body.count & 0xff)])
        result.append(body)
        return result
    }
}

struct BleFrameDecoder {
    private var buffer = Data()

    mutating func append(_ fragment: Data) throws -> [BleFrame] {
        buffer.append(fragment)
        guard buffer.count <= 530 * 4 else {
            buffer.removeAll()
            throw BleProtocolError.invalidLength
        }
        var frames: [BleFrame] = []
        while buffer.count >= 2 {
            let bodyLength = (Int(buffer[0]) << 8) | Int(buffer[1])
            guard bodyLength >= 18 && bodyLength <= 530 else {
                buffer.removeAll()
                throw BleProtocolError.invalidLength
            }
            guard buffer.count >= bodyLength + 2 else { break }
            let body = buffer.subdata(in: 2..<(bodyLength + 2))
            buffer.removeSubrange(0..<(bodyLength + 2))
            guard body[0] == 1 else { throw BleProtocolError.unsupportedVersion }
            guard let type = BleMessage(rawValue: body[1]) else { throw BleProtocolError.unknownMessage }
            guard let payload = String(data: body.subdata(in: 18..<body.count), encoding: .utf8) else {
                throw BleProtocolError.invalidUtf8
            }
            let requestId = UUID(uuid: (
                body[2], body[3], body[4], body[5],
                body[6], body[7], body[8], body[9],
                body[10], body[11], body[12], body[13],
                body[14], body[15], body[16], body[17]
            ))
            frames.append(BleFrame(type: type, requestId: requestId, payload: payload))
        }
        return frames
    }
}



