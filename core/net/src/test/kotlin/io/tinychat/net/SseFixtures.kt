package io.tinychat.net

/**
 * SSE 离线固件：全部帧形状取自 docs/03-handoff.md §6.3 的一手抓包，不自己发明字段。
 * 由 Spike 的一次性验证工程原样搬入，作为 M1-2 / M1-4 的回归基线。
 */
object SseFixtures {

    const val SEARCH_STREAM: String = """: keep-alive

event: message_start
data: {"type":"message_start","message":{"id":"b161a9be-0000-4000-8000-000000000001","type":"message","role":"assistant","model":"deepseek-flash","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":40,"cache_creation_input_tokens":0,"cache_read_input_tokens":0,"output_tokens":0,"service_tier":"standard"}}}

event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}

event: ping
data: {"type":"ping"}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"先确认最新版本。"}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig_abcdefghij"}}

event: content_block_stop
data: {"type":"content_block_stop","index":0}

event: content_block_start
data: {"type":"content_block_start","index":1,"content_block":{"type":"server_tool_use","id":"srvtoolu_ds_00_b2baebaf","name":"deepseek_web_search","input":{"action":"search","queries":["DeepSeek V4.1 new features","DeepSeek V4.1 价格"]},"caller":{"type":"direct"},"kind":"deep_search"}}

event: content_block_stop
data: {"type":"content_block_stop","index":1}

event: content_block_start
data: {"type":"content_block_start","index":2,"content_block":{"type":"web_search_tool_result","tool_use_id":"srvtoolu_ds_00_b2baebaf","content":[{"type":"web_search_result","title":"A","url":"https://a.example.com","encrypted_content":"xx","page_age":"2026-10-16T16:00:00+00:00","snippet":"sa","site_name":"DeepSeek","site_icon":"https://cdn.deepseek.com/site-icons/deepseek.com"},{"type":"web_search_result","title":"B","url":"https://b.example.com","encrypted_content":"yy","page_age":null,"snippet":"sb","site_name":"B","site_icon":null}],"kind":"deep_search"}}

event: content_block_stop
data: {"type":"content_block_stop","index":2}

event: content_block_start
data: {"type":"content_block_start","index":3,"content_block":{"type":"thinking","thinking":"","signature":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":3,"delta":{"type":"thinking_delta","thinking":"整理成三点。"}}

event: content_block_stop
data: {"type":"content_block_stop","index":3}

event: content_block_start
data: {"type":"content_block_start","index":4,"content_block":{"type":"text","text":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":4,"delta":{"type":"text_delta","text":"DeepSeek V4.1 主要更新："}}

event: content_block_delta
data: {"type":"content_block_delta","index":4,"delta":{"type":"text_delta","text":"\n1. 上下文 1M"}}

event: content_block_delta
data: {"type":"content_block_delta","index":4,"delta":{"type":"citations_delta","citation":{"type":"web_search_result_location","url":"https://a.example.com","title":"A","encrypted_index":"","cited_text":"1M context"}}}

event: content_block_stop
data: {"type":"content_block_stop","index":4}

event: content_block_start
data: {"type":"content_block_start","index":5,"content_block":{"type":"text","text":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":5,"delta":{"type":"text_delta","text":"\n2. 输出 384K"}}

event: content_block_stop
data: {"type":"content_block_stop","index":5}

event: message_delta
data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null,"title":"DeepSeek V4.1 更新"},"usage":{"input_tokens":9078,"cache_creation_input_tokens":0,"cache_read_input_tokens":4096,"output_tokens":1686,"service_tier":"standard","server_tool_use":{"web_search_requests":1,"web_fetch_requests":0,"tool_search":2,"tool_fetch":0,"tool_image_search":0}}}

event: message_stop
data: {"type":"message_stop"}
"""

    const val READ_LINK_STREAM: String = """event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"server_tool_use","id":"srvtoolu_rl_01_9c1d","name":"deepseek_read_url","input":{"action":"read_link","url":"https://example.com/a"},"caller":{"type":"direct"},"kind":"read_link"}}

event: content_block_stop
data: {"type":"content_block_stop","index":0}

event: content_block_start
data: {"type":"content_block_start","index":1,"content_block":{"type":"web_fetch_tool_result","tool_use_id":"srvtoolu_rl_01_9c1d","content":{"type":"web_fetch_result","url":"https://example.com/a","content":{"type":"document","source":{"type":"text","media_type":"text/plain","data":""},"title":"页面标题"},"published_at":"2026-01-01"},"kind":"read_link","signature":"q3J0b1RmZ1c4cE1x"}}

event: content_block_stop
data: {"type":"content_block_stop","index":1}
"""

    const val FETCH_ERROR_STREAM: String = """event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"web_fetch_tool_result","tool_use_id":"srvtoolu_rl_02","content":{"type":"web_fetch_tool_result_error","error_code":"url_not_accessible"},"kind":"read_link"}}
"""

    const val REFUSAL_STREAM: String = """event: message_start
data: {"type":"message_start","message":{"id":"m-refusal","type":"message","role":"assistant","model":"deepseek-flash","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":30,"cache_creation_input_tokens":0,"cache_read_input_tokens":0,"output_tokens":0,"service_tier":"standard"}}}

event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"抱歉，这个我没法帮。"}}

event: content_block_stop
data: {"type":"content_block_stop","index":0}

event: message_delta
data: {"type":"message_delta","delta":{"stop_reason":"refusal","stop_sequence":null,"stop_details":{"type":"refusal","category":null,"explanation":null}},"usage":{"input_tokens":31,"cache_creation_input_tokens":0,"cache_read_input_tokens":0,"output_tokens":12,"service_tier":"standard"}}

event: message_stop
data: {"type":"message_stop"}
"""

    const val SERVER_ERROR_STREAM: String = """event: error
data: {"type":"error","error":{"type":"api_error","message":"The model failed to generate a complete response. Please retry."},"usage":{"input_tokens":10,"cache_creation_input_tokens":0,"cache_read_input_tokens":0,"output_tokens":0}}
"""

    /** 官方文档未列的事件：必须只登记、不崩。 */
    const val FUTURE_EVENT_STREAM: String = """event: future_thing
data: {"type":"future_thing","foo":1}
"""
}
