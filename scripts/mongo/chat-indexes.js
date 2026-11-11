db.chat_messages.createIndex({ room_id: 1, seq: 1 }, { name: "uk_chat_messages_room_seq", unique: true });
db.chat_messages.createIndex({ room_id: 1, client_message_id: 1 }, { name: "uk_chat_messages_room_client", unique: true });
print("chat indexes ready");
