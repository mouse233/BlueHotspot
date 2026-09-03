import { util } from '@kit.ArkTS';

const PROTOCOL_VERSION: number = 1;
const HEADER_SIZE: number = 18;
const MAX_PAYLOAD_SIZE: number = 512;
const MAX_BODY_SIZE: number = HEADER_SIZE + MAX_PAYLOAD_SIZE;
const MAX_BUFFER_SIZE: number = MAX_BODY_SIZE * 4;

export enum BleMessage {
  HELLO = 1,
  HELLO_ACK = 2,
  GET_STATUS = 3,
  STATUS = 4,
  START_HOTSPOT = 5,
  HOTSPOT_STARTING = 6,
  HOTSPOT_READY = 7,
  HOTSPOT_FAILED = 8,
  STOP_HOTSPOT = 9,
  HOTSPOT_STOPPED = 10,
  PING = 11,
  PONG = 12,
  ERROR = 13
}

export class BleFrame {
  type: BleMessage;
  requestId: string;
  payload: string;

  constructor(type: BleMessage, requestId: string, payload: string = '') {
    this.type = type;
    this.requestId = requestId;
    this.payload = payload;
  }
}

export class BleProtocolError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'BleProtocolError';
  }
}

function hexValue(value: string): number {
  const result: number = Number.parseInt(value, 16);
  if (Number.isNaN(result)) {
    throw new BleProtocolError('invalid UUID');
  }
  return result;
}

export function uuidToBytes(uuid: string): Uint8Array {
  const compact: string = uuid.replaceAll('-', '');
  if (compact.length !== 32 || !/^[0-9a-fA-F]+$/.test(compact)) {
    throw new BleProtocolError('invalid UUID');
  }
  const bytes: Uint8Array = new Uint8Array(16);
  for (let index: number = 0; index < 16; index++) {
    bytes[index] = hexValue(compact.substring(index * 2, index * 2 + 2));
  }
  return bytes;
}

export function bytesToUuid(bytes: Uint8Array): string {
  if (bytes.length !== 16) {
    throw new BleProtocolError('invalid UUID byte length');
  }
  let hex: string = '';
  for (let index: number = 0; index < bytes.length; index++) {
    hex += bytes[index].toString(16).padStart(2, '0');
  }
  return `${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}`;
}

export function newRequestId(): string {
  return util.generateRandomUUID();
}

function utf8Encode(value: string): Uint8Array {
  const expected: Uint8Array = manualUtf8Encode(value);
  try {
    const encoded: Uint8Array = util.TextEncoder.create('utf-8').encodeInto(value);
    if (sameBytes(encoded, expected)) {
      return encoded;
    }
  } catch (_) {
    // Fall through to the strict implementation below.
  }
  return expected;
}

function sameBytes(left: Uint8Array, right: Uint8Array): boolean {
  if (left.length !== right.length) {
    return false;
  }
  for (let index: number = 0; index < left.length; index++) {
    if (left[index] !== right[index]) {
      return false;
    }
  }
  return true;
}

function manualUtf8Encode(value: string): Uint8Array {
  let byteLength: number = 0;
  for (let index: number = 0; index < value.length; index++) {
    const unit: number = value.charCodeAt(index);
    if (unit >= 0xd800 && unit <= 0xdbff) {
      if (index + 1 >= value.length) {
        throw new BleProtocolError('payload contains an unpaired UTF-16 surrogate');
      }
      const low: number = value.charCodeAt(index + 1);
      if (low < 0xdc00 || low > 0xdfff) {
        throw new BleProtocolError('payload contains an unpaired UTF-16 surrogate');
      }
      byteLength += 4;
      index += 1;
    } else if (unit >= 0xdc00 && unit <= 0xdfff) {
      throw new BleProtocolError('payload contains an unpaired UTF-16 surrogate');
    } else if (unit <= 0x7f) {
      byteLength += 1;
    } else if (unit <= 0x7ff) {
      byteLength += 2;
    } else {
      byteLength += 3;
    }
  }

  const result: Uint8Array = new Uint8Array(byteLength);
  let output: number = 0;
  for (let index: number = 0; index < value.length; index++) {
    let codePoint: number = value.charCodeAt(index);
    if (codePoint >= 0xd800 && codePoint <= 0xdbff) {
      const low: number = value.charCodeAt(index + 1);
      codePoint = 0x10000 + ((codePoint - 0xd800) << 10) + (low - 0xdc00);
      index += 1;
    }
    if (codePoint <= 0x7f) {
      result[output] = codePoint;
      output += 1;
    } else if (codePoint <= 0x7ff) {
      result[output] = 0xc0 | (codePoint >> 6);
      result[output + 1] = 0x80 | (codePoint & 0x3f);
      output += 2;
    } else if (codePoint <= 0xffff) {
      result[output] = 0xe0 | (codePoint >> 12);
      result[output + 1] = 0x80 | ((codePoint >> 6) & 0x3f);
      result[output + 2] = 0x80 | (codePoint & 0x3f);
      output += 3;
    } else {
      result[output] = 0xf0 | (codePoint >> 18);
      result[output + 1] = 0x80 | ((codePoint >> 12) & 0x3f);
      result[output + 2] = 0x80 | ((codePoint >> 6) & 0x3f);
      result[output + 3] = 0x80 | (codePoint & 0x3f);
      output += 4;
    }
  }
  return result;
}

function isContinuation(value: number): boolean {
  return value >= 0x80 && value <= 0xbf;
}

function validateUtf8(value: Uint8Array): void {
  let index: number = 0;
  while (index < value.length) {
    const first: number = value[index];
    if (first <= 0x7f) {
      index += 1;
    } else if (first >= 0xc2 && first <= 0xdf) {
      if (index + 1 >= value.length || !isContinuation(value[index + 1])) {
        throw new BleProtocolError('payload is not valid UTF-8');
      }
      index += 2;
    } else if (first === 0xe0) {
      if (index + 2 >= value.length || value[index + 1] < 0xa0 || value[index + 1] > 0xbf ||
        !isContinuation(value[index + 2])) {
        throw new BleProtocolError('payload is not valid UTF-8');
      }
      index += 3;
    } else if ((first >= 0xe1 && first <= 0xec) || (first >= 0xee && first <= 0xef)) {
      if (index + 2 >= value.length || !isContinuation(value[index + 1]) || !isContinuation(value[index + 2])) {
        throw new BleProtocolError('payload is not valid UTF-8');
      }
      index += 3;
    } else if (first === 0xed) {
      if (index + 2 >= value.length || value[index + 1] < 0x80 || value[index + 1] > 0x9f ||
        !isContinuation(value[index + 2])) {
        throw new BleProtocolError('payload is not valid UTF-8');
      }
      index += 3;
    } else if (first === 0xf0) {
      if (index + 3 >= value.length || value[index + 1] < 0x90 || value[index + 1] > 0xbf ||
        !isContinuation(value[index + 2]) || !isContinuation(value[index + 3])) {
        throw new BleProtocolError('payload is not valid UTF-8');
      }
      index += 4;
    } else if (first >= 0xf1 && first <= 0xf3) {
      if (index + 3 >= value.length || !isContinuation(value[index + 1]) ||
        !isContinuation(value[index + 2]) || !isContinuation(value[index + 3])) {
        throw new BleProtocolError('payload is not valid UTF-8');
      }
      index += 4;
    } else if (first === 0xf4) {
      if (index + 3 >= value.length || value[index + 1] < 0x80 || value[index + 1] > 0x8f ||
        !isContinuation(value[index + 2]) || !isContinuation(value[index + 3])) {
        throw new BleProtocolError('payload is not valid UTF-8');
      }
      index += 4;
    } else {
      throw new BleProtocolError('payload is not valid UTF-8');
    }
  }
}

function utf8Decode(value: Uint8Array): string {
  try {
    validateUtf8(value);
    const decoder: util.TextDecoder = util.TextDecoder.create('utf-8', { fatal: true });
    const decoded: string = decoder.decodeToString(value, { stream: false });
    const strictDecoded: string = manualUtf8Decode(value);
    return decoded === strictDecoded ? decoded : strictDecoded;
  } catch (_) {
    if (_ instanceof BleProtocolError) {
      throw _;
    }
    throw new BleProtocolError('payload is not valid UTF-8');
  }
}

function manualUtf8Decode(value: Uint8Array): string {
  let result: string = '';
  let index: number = 0;
  while (index < value.length) {
    const first: number = value[index];
    let codePoint: number;
    let width: number;
    if (first <= 0x7f) {
      codePoint = first;
      width = 1;
    } else if (first <= 0xdf) {
      codePoint = ((first & 0x1f) << 6) | (value[index + 1] & 0x3f);
      width = 2;
    } else if (first <= 0xef) {
      codePoint = ((first & 0x0f) << 12) | ((value[index + 1] & 0x3f) << 6) |
        (value[index + 2] & 0x3f);
      width = 3;
    } else {
      codePoint = ((first & 0x07) << 18) | ((value[index + 1] & 0x3f) << 12) |
        ((value[index + 2] & 0x3f) << 6) | (value[index + 3] & 0x3f);
      width = 4;
    }
    result += String.fromCodePoint(codePoint);
    index += width;
  }
  return result;
}

export class BleFrameCodec {
  static encode(frame: BleFrame): ArrayBuffer {
    const payload: Uint8Array = utf8Encode(frame.payload);
    if (payload.length > MAX_PAYLOAD_SIZE) {
      throw new BleProtocolError('payload is too large');
    }
    const bodyLength: number = HEADER_SIZE + payload.length;
    const body: Uint8Array = new Uint8Array(bodyLength);
    body[0] = PROTOCOL_VERSION;
    body[1] = frame.type;
    body.set(uuidToBytes(frame.requestId), 2);
    body.set(payload, HEADER_SIZE);

    const result: Uint8Array = new Uint8Array(bodyLength + 2);
    new DataView(result.buffer).setUint16(0, bodyLength, false);
    result.set(body, 2);
    return result.buffer;
  }

  static decode(body: Uint8Array): BleFrame {
    if (body.length < HEADER_SIZE || body.length > MAX_BODY_SIZE) {
      throw new BleProtocolError(`invalid body length: ${body.length}`);
    }
    if (body[0] !== PROTOCOL_VERSION) {
      throw new BleProtocolError('unsupported protocol version');
    }
    const type: number = body[1];
    if (type < BleMessage.HELLO || type > BleMessage.ERROR) {
      throw new BleProtocolError(`unknown message type: ${type}`);
    }
    const requestId: string = bytesToUuid(body.slice(2, 18));
    const payload: string = utf8Decode(body.slice(18));
    return new BleFrame(type as BleMessage, requestId, payload);
  }
}

export class BleFrameDecoder {
  private buffer: Uint8Array = new Uint8Array(0);

  append(fragment: ArrayBuffer): Array<BleFrame> {
    const incoming: Uint8Array = new Uint8Array(fragment);
    if (incoming.length === 0) {
      return [];
    }
    if (this.buffer.length + incoming.length > MAX_BUFFER_SIZE) {
      this.buffer = new Uint8Array(0);
      throw new BleProtocolError('frame stream is too large');
    }
    const combined: Uint8Array = new Uint8Array(this.buffer.length + incoming.length);
    combined.set(this.buffer);
    combined.set(incoming, this.buffer.length);
    this.buffer = combined;

    const frames: Array<BleFrame> = [];
    while (this.buffer.length >= 2) {
      const bodyLength: number = new DataView(this.buffer.buffer, this.buffer.byteOffset, 2).getUint16(0, false);
      if (bodyLength < HEADER_SIZE || bodyLength > MAX_BODY_SIZE) {
        this.buffer = new Uint8Array(0);
        throw new BleProtocolError(`invalid frame length: ${bodyLength}`);
      }
      if (this.buffer.length < bodyLength + 2) {
        break;
      }
      frames.push(BleFrameCodec.decode(this.buffer.slice(2, bodyLength + 2)));
      this.buffer = this.buffer.slice(bodyLength + 2);
    }
    return frames;
  }

  reset(): void {
    this.buffer = new Uint8Array(0);
  }
}

export function splitForGatt(encoded: ArrayBuffer, mtu: number): Array<ArrayBuffer> {
  const bytes: Uint8Array = new Uint8Array(encoded);
  const chunkSize: number = mtu <= 23 ? 20 : Math.min(180, mtu - 3);
  const chunks: Array<ArrayBuffer> = [];
  for (let offset: number = 0; offset < bytes.length; offset += chunkSize) {
    const end: number = Math.min(offset + chunkSize, bytes.length);
    const chunk: Uint8Array = new Uint8Array(end - offset);
    chunk.set(bytes.subarray(offset, end));
    chunks.push(chunk.buffer as ArrayBuffer);
  }
  return chunks;
}

export class BleWriteQueue {
  private readonly chunks: Array<ArrayBuffer> = [];

  enqueue(encoded: ArrayBuffer, mtu: number): void {
    this.chunks.push(...splitForGatt(encoded, mtu));
  }

  take(): ArrayBuffer | undefined {
    return this.chunks.shift();
  }

  clear(): void {
    this.chunks.length = 0;
  }

  get length(): number {
    return this.chunks.length;
  }
}
