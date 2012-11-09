function (doc, meta) {
    if(doc.body) {
        var words = doc.body.split(/\s+/);
        if (words.length >= 1) {
            emit([null, words[0]], 1);
        }
        for(var i = 0; i < (words.length - 1); i++) {
            var pair = [words[i], words[i+1]];
            emit(pair, 1);
        }
    }
}
