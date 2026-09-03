import { describe, expect, it } from '@ohos/hypium';
import { BleFrame, BleFrameCodec, BleFrameDecoder, BleMessage, BleProtocolError, BleWriteQueue, bytesToUuid, splitForGatt, uuidToBytes } from '../../main/ets/ble/BleProtocol';

const REQUEST_ID: string = '00112233-4455-6677-8899-aabbccddeeff';

function asArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer as ArrayBuffer;
}

export default function bleProtocolTest(): void {
  describe('BlueHotspot BLE protocol', (): void => {
    it('round trips a fragmented frame', 0, (): void => {
      const encoded: ArrayBuffer = BleFrameCodec.encode(new BleFrame(BleMessage.START_HOTSPOT, REQUEST_ID));
      const decoder: BleFrameDecoder = new BleFrameDecoder();
      const bytes: Uint8Array = new Uint8Array(encoded);
      const frames: Array<BleFrame> = [];
      for (let offset: number = 0; offset < bytes.length; offset += 3) {
        frames.push(...decoder.append(asArrayBuffer(bytes.slice(offset, Math.min(offset + 3, bytes.length)))));
      }
      expect(frames.length).assertEqual(1);
      expect(frames[0].type).assertEqual(BleMessage.START_HOTSPOT);
      expect(frames[0].requestId).assertEqual(REQUEST_ID);
      expect(frames[0].payload).assertEqual('');
    });

    it('decodes multiple frames from one stream', 0, (): void => {
      const first: ArrayBuffer = BleFrameCodec.encode(new BleFrame(BleMessage.PING, REQUEST_ID));
      const second: ArrayBuffer = BleFrameCodec.encode(new BleFrame(BleMessage.STATUS, REQUEST_ID, 'ACTIVE'));
      const combined: Uint8Array = new Uint8Array(first.byteLength + second.byteLength);
      combined.set(new Uint8Array(first));
      combined.set(new Uint8Array(second), first.byteLength);
      const frames: Array<BleFrame> = new BleFrameDecoder().append(asArrayBuffer(combined));
      expect(frames.length).assertEqual(2);
      expect(frames[1].payload).assertEqual('ACTIVE');
    });

    it('rejects oversized payloads', 0, (): void => {
      expect((): ArrayBuffer => BleFrameCodec.encode(new BleFrame(BleMessage.STATUS, REQUEST_ID, 'x'.repeat(513))))
        .assertThrowError(BleProtocolError);
    });

    it('rejects invalid frame lengths', 0, (): void => {
      expect((): Array<BleFrame> => new BleFrameDecoder().append(asArrayBuffer(new Uint8Array([0, 17]))))
        .assertThrowError(BleProtocolError);
      const bodyTooLarge: Uint8Array = new Uint8Array([2, 0x13]);
      expect((): Array<BleFrame> => new BleFrameDecoder().append(asArrayBuffer(bodyTooLarge)))
        .assertThrowError(BleProtocolError);
      const unknown: Uint8Array = new Uint8Array(20);
      unknown[1] = 18;
      unknown[2] = 1;
      unknown[3] = 14;
      expect((): Array<BleFrame> => new BleFrameDecoder().append(asArrayBuffer(unknown)))
        .assertThrowError(BleProtocolError);
    });

    it('rejects invalid UTF-8', 0, (): void => {
      const invalid: Uint8Array = new Uint8Array(19);
      invalid[0] = 1;
      invalid[1] = BleMessage.STATUS;
      invalid[18] = 0xff;
      const framed: Uint8Array = new Uint8Array([0, 19, ...invalid]);
      expect((): Array<BleFrame> => new BleFrameDecoder().append(asArrayBuffer(framed)))
        .assertThrowError(BleProtocolError);
    });

    it('preserves UUID network byte order', 0, (): void => {
      const bytes: Uint8Array = uuidToBytes(REQUEST_ID);
      expect(bytes[0]).assertEqual(0x00);
      expect(bytes[1]).assertEqual(0x11);
      expect(bytes[15]).assertEqual(0xff);
      expect(bytesToUuid(bytes)).assertEqual(REQUEST_ID);
    });

    it('splits writes in protocol-compatible chunks', 0, (): void => {
      const encoded: ArrayBuffer = BleFrameCodec.encode(new BleFrame(BleMessage.STATUS, REQUEST_ID, 'x'.repeat(512)));
      const chunks: Array<ArrayBuffer> = splitForGatt(encoded, 247);
      expect(chunks.length).assertEqual(3);
      expect(chunks[0].byteLength).assertEqual(180);
      expect(chunks[1].byteLength).assertEqual(180);
      expect(chunks[2].byteLength).assertEqual(172);
      expect(splitForGatt(encoded, 23)[0].byteLength).assertEqual(20);
    });

    it('writes queued chunks in order', 0, (): void => {
      const queue: BleWriteQueue = new BleWriteQueue();
      queue.enqueue(BleFrameCodec.encode(new BleFrame(BleMessage.HELLO, REQUEST_ID)), 23);
      queue.enqueue(BleFrameCodec.encode(new BleFrame(BleMessage.PING, REQUEST_ID)), 23);
      const first: ArrayBuffer | undefined = queue.take();
      const second: ArrayBuffer | undefined = queue.take();
      expect(first === undefined).assertFalse();
      expect(second === undefined).assertFalse();
      if (first !== undefined && second !== undefined) {
        expect(new Uint8Array(first)[3]).assertEqual(BleMessage.HELLO);
        expect(new Uint8Array(second)[3]).assertEqual(BleMessage.PING);
      }
      expect(queue.length).assertEqual(0);
    });
  });
}
